// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Raw libfabric calls: querying what the host offers and turning return codes into [`RdmaError`]s.

use std::ffi::{CStr, CString};
use std::ptr::{self, NonNull};
use std::sync::{Arc, Mutex, PoisonError, Weak};

use tokio::sync::watch;

use ofi_libfabric_sys::bindgen::{
    FI_CONTEXT2, FI_MR_ALLOCATED, FI_MR_LOCAL, FI_MR_PROV_KEY, FI_MR_VIRT_ADDR, FI_MSG, FI_READ,
    FI_RECV, FI_REMOTE_READ, FI_REMOTE_WRITE, FI_RMA, FI_SOURCE, FI_TRANSMIT, FI_WRITE, fi_addr_t,
    fi_allocinfo, fi_av_attr, fi_av_insert, fi_av_open, fi_av_remove, fi_av_type_FI_AV_MAP,
    fi_close, fi_cq_attr, fi_cq_format_FI_CQ_FORMAT_CONTEXT, fi_cq_open, fi_domain, fi_domain_attr,
    fi_dupinfo, fi_enable, fi_endpoint, fi_ep_bind, fi_ep_type_FI_EP_RDM, fi_fabric, fi_freeinfo,
    fi_getinfo, fi_getname, fi_info, fi_mr_key, fi_mr_reg, fi_strerror, fi_threading,
    fi_threading_FI_THREAD_COMPLETION, fi_threading_FI_THREAD_DOMAIN,
    fi_threading_FI_THREAD_ENDPOINT, fi_threading_FI_THREAD_FID, fi_threading_FI_THREAD_SAFE,
    fi_threading_FI_THREAD_UNSPEC, fid_av, fid_cq, fid_domain, fid_ep, fid_fabric, fid_mr,
};

use crate::config::{FabricConfig, Provider};
use crate::error::RdmaError;
use crate::fabric::RdmaFabric;
use crate::libfabric_dl::header_api_version;

/// Turn a libfabric return code into a `Result`: 0 for success, negative `-errno` otherwise.
pub(crate) fn check(code: i32, operation: &'static str) -> Result<(), RdmaError> {
    if code == 0 {
        return Ok(());
    }
    // libfabric returns a negative errno; fi_strerror wants the positive one.
    // SAFETY: fi_strerror returns a pointer to a static, NUL-terminated string.
    let message = unsafe { CStr::from_ptr(fi_strerror(-code)) }
        .to_string_lossy()
        .into_owned();
    Err(RdmaError::Fabric {
        operation,
        message,
        errno: Some(code),
    })
}

/// Reject configurations libfabric would only fail on with a misleading message.
fn validate(config: &FabricConfig) -> Result<(), RdmaError> {
    if config.provider() == Provider::EfaDirect && config.bind().is_some() {
        return Err(RdmaError::Configuration(
            "bind is not meaningful on efa-direct: the endpoint advertises an opaque \
             fabric address, not one you choose. Leave it unset."
                .into(),
        ));
    }
    Ok(())
}

/// Build the configured provider's hints and run `fi_getinfo`.
/// The caller selects an entry and frees the list with `fi_freeinfo`.
pub(crate) fn query_info(config: &FabricConfig) -> Result<*mut fi_info, RdmaError> {
    // A provider driven by a poller thread needs a domain that tolerates
    // concurrent use; see the SAFETY note on `LibfabricEndpoint`. efa-direct is
    // left unconstrained: it is never polled, so it never needs this.
    let required = config
        .provider()
        .needs_manual_progress()
        .then_some(fi_threading_FI_THREAD_SAFE);
    query_info_requiring(config, required)
}

/// `query_info` with the threading model named rather than inferred. Tests use this
/// to drive the requirement against a provider that will not meet it.
pub(crate) fn query_info_requiring(
    config: &FabricConfig,
    required: Option<fi_threading>,
) -> Result<*mut fi_info, RdmaError> {
    let error = match query_info_with(config, required) {
        Ok(info) => return Ok(info),
        Err(error) => error,
    };

    // libfabric honours a threading request by leaving out every provider that
    // cannot meet it, so the failure above is indistinguishable from the provider
    // being absent. Ask again without the requirement: if that finds something,
    // the requirement is the real reason and the message should say so.
    let Some(required) = required else {
        return Err(error);
    };
    let Ok(offered) = query_info_with(config, None) else {
        return Err(error);
    };
    // SAFETY: an owned list from fi_getinfo, read once and freed here.
    let model = unsafe { domain_attr(offered) }
        .map_or(fi_threading_FI_THREAD_UNSPEC, |attr| attr.threading);
    unsafe { fi_freeinfo(offered) };
    Err(RdmaError::Configuration(format!(
        "the {} provider offers the {} threading model, but this client polls it for \
         progress and that needs {}",
        config.provider().as_str(),
        threading_name(model),
        threading_name(required),
    )))
}

/// The domain attributes of one `fi_info` entry or `None` if the entry or its
/// attributes are null.
///
/// libfabric fills in `domain_attr` on every entry it returns. This checks anyway,
/// so that a provider that breaks that rule gets skipped instead of crashing the
/// client.
///
/// # Safety
/// `info` must be null or point to an `fi_info` that stays allocated for as long as
/// the returned reference is used.
unsafe fn domain_attr<'a>(info: *const fi_info) -> Option<&'a fi_domain_attr> {
    // SAFETY: the caller guarantees `info` is null or live, and libfabric keeps a
    // non-null `domain_attr` allocated for as long as its `fi_info`.
    unsafe { info.as_ref()?.domain_attr.as_ref() }
}

/// libfabric's name for a threading model, for a message a reader can act on.
fn threading_name(model: fi_threading) -> &'static str {
    // Bound to local names first: the generated constants are lower case, which
    // a pattern would read as fresh bindings that match anything.
    const UNSPEC: fi_threading = fi_threading_FI_THREAD_UNSPEC;
    const SAFE: fi_threading = fi_threading_FI_THREAD_SAFE;
    const FID: fi_threading = fi_threading_FI_THREAD_FID;
    const DOMAIN: fi_threading = fi_threading_FI_THREAD_DOMAIN;
    const COMPLETION: fi_threading = fi_threading_FI_THREAD_COMPLETION;
    const ENDPOINT: fi_threading = fi_threading_FI_THREAD_ENDPOINT;

    match model {
        UNSPEC => "FI_THREAD_UNSPEC",
        SAFE => "FI_THREAD_SAFE",
        FID => "FI_THREAD_FID",
        DOMAIN => "FI_THREAD_DOMAIN",
        COMPLETION => "FI_THREAD_COMPLETION",
        ENDPOINT => "FI_THREAD_ENDPOINT",
        _ => "an unrecognised",
    }
}

fn query_info_with(
    config: &FabricConfig,
    threading: Option<fi_threading>,
) -> Result<*mut fi_info, RdmaError> {
    validate(config)?;

    let provider = CString::new(config.provider().as_str())
        .map_err(|_| RdmaError::Configuration("invalid provider name".into()))?;
    let fabric_name = match config.provider().fabric_name() {
        Some(name) => Some(
            CString::new(name)
                .map_err(|_| RdmaError::Configuration("invalid fabric name".into()))?,
        ),
        None => None,
    };
    let node = match config.bind() {
        Some(node) => Some(
            CString::new(node).map_err(|_| RdmaError::Configuration("invalid bind node".into()))?,
        ),
        None => None,
    };

    let mut info: *mut fi_info = ptr::null_mut();
    // SAFETY: `hints` is allocated by libfabric and freed below on every path. The
    // CString pointers written into it are detached before that free, so libfabric
    // never frees Rust-owned memory.
    unsafe {
        let hints = fi_allocinfo();
        if hints.is_null() {
            return Err(RdmaError::Fabric {
                operation: "fi_allocinfo",
                message: "returned null".into(),
                errno: None,
            });
        }
        // libfabric allocates all three, but a null one would be written through below.
        if (*hints).ep_attr.is_null()
            || (*hints).domain_attr.is_null()
            || (*hints).fabric_attr.is_null()
        {
            fi_freeinfo(hints);
            return Err(RdmaError::Fabric {
                operation: "fi_allocinfo",
                message: "returned an entry without its attributes".into(),
                errno: None,
            });
        }
        let caps =
            u64::from(FI_MSG | FI_RMA | FI_READ | FI_WRITE | FI_REMOTE_READ | FI_REMOTE_WRITE);
        // Leaving mr_mode 0 makes efa hand back a variant whose RMA still needs
        // virtual addresses while reporting mr_mode=0, which then fails with
        // REMOTE_BAD_ADDRESS.
        let mr_mode = FI_MR_LOCAL | FI_MR_ALLOCATED | FI_MR_PROV_KEY | FI_MR_VIRT_ADDR;
        (*hints).caps = caps;
        (*(*hints).ep_attr).type_ = fi_ep_type_FI_EP_RDM;
        (*(*hints).domain_attr).mr_mode = mr_mode as i32;
        // addr_format stays unspecified so each provider picks its native format;
        // addresses are exchanged opaquely.
        (*(*hints).fabric_attr).prov_name = provider.as_ptr().cast_mut();
        if let Some(name) = &fabric_name {
            (*(*hints).fabric_attr).name = name.as_ptr().cast_mut();
        }
        if config.provider().requires_context2() {
            (*hints).mode |= FI_CONTEXT2;
        }
        if let Some(threading) = threading {
            (*(*hints).domain_attr).threading = threading;
        }

        let (node_pointer, flags) = match &node {
            Some(node) => (node.as_ptr(), FI_SOURCE),
            None => (ptr::null(), 0),
        };
        let code = fi_getinfo(
            header_api_version(),
            node_pointer,
            ptr::null(),
            flags,
            hints,
            &mut info,
        );
        // Detach these so fi_freeinfo does not free the Rust-owned CStrings.
        (*(*hints).fabric_attr).prov_name = ptr::null_mut();
        (*(*hints).fabric_attr).name = ptr::null_mut();
        fi_freeinfo(hints);
        check(code, "fi_getinfo")?;
    }
    if info.is_null() {
        return Err(RdmaError::Fabric {
            operation: "fi_getinfo",
            message: "no provider matched".into(),
            errno: None,
        });
    }
    Ok(info)
}

/// The distinct fabric domain names in `list` in the order libfabric returned them.
///
/// Borrows the list rather than querying so a caller already holding one can name what
/// the host has without a second `fi_getinfo`.
pub(crate) fn domain_names(list: *mut fi_info) -> Vec<String> {
    let mut names: Vec<String> = Vec::new();
    // SAFETY: a valid fi_info list from fi_getinfo, only read.
    for name in unsafe { entries(list) }.filter_map(|info| unsafe { domain_name(info) }) {
        if !names.iter().any(|existing| existing == name) {
            names.push(name.to_string());
        }
    }
    names
}

/// The first entry in `list` whose fabric domain is named `want`, or null.
fn select_domain(list: *mut fi_info, want: &str) -> *mut fi_info {
    // SAFETY: a valid fi_info list from fi_getinfo, only read.
    unsafe { entries(list) }
        .find(|info| unsafe { domain_name(info) } == Some(want))
        .map_or(ptr::null_mut(), |info| ptr::from_ref(info).cast_mut())
}

/// Each entry of an `fi_info` list, in order.
///
/// # Safety
/// `list` must be null or the head of an `fi_info` list that stays allocated for as
/// long as the entries are used.
unsafe fn entries<'a>(list: *const fi_info) -> impl Iterator<Item = &'a fi_info> {
    // SAFETY: the caller guarantees every entry reached through `next` is live.
    std::iter::successors(unsafe { list.as_ref() }, |info| unsafe {
        info.next.as_ref()
    })
}

/// The fabric domain name of one entry, or `None` if it has no readable name.
///
/// # Safety
/// As for [`domain_attr`].
unsafe fn domain_name(info: &fi_info) -> Option<&str> {
    // SAFETY: the caller guarantees `info` is live.
    let name = unsafe { domain_attr(info) }?.name;
    if name.is_null() {
        return None;
    }
    // SAFETY: libfabric names are NUL-terminated and live as long as their entry.
    unsafe { CStr::from_ptr(name) }.to_str().ok()
}

/// The object a successful `fi_*_open` call wrote out, or an error if it wrote null.
///
/// # Safety
/// `pointer` must be null or point to an open object that stays open for as long as
/// the returned reference is used.
unsafe fn opened<'a, T>(pointer: *mut T, operation: &'static str) -> Result<&'a mut T, RdmaError> {
    // SAFETY: the caller guarantees `pointer` is null or live.
    unsafe { pointer.as_mut() }.ok_or_else(|| RdmaError::Fabric {
        operation,
        message: "returned null".into(),
        errno: None,
    })
}

/// Where a registration's remote key comes from, per `FI_MR_PROV_KEY`.
#[derive(Debug)]
enum RKeySource {
    /// The provider assigns the key, so registration must not request one.
    ProviderSelected,
    /// Requesting one key twice on a domain fails the second registration with
    /// `-FI_ENOKEY`, so they are handed out in sequence.
    ApplicationSelected { next_remote_key: u64 },
}

impl RKeySource {
    fn take(&mut self) -> u64 {
        match self {
            RKeySource::ProviderSelected => 0,
            RKeySource::ApplicationSelected { next_remote_key } => {
                let key = *next_remote_key;
                *next_remote_key += 1;
                key
            }
        }
    }
}

/// Access mask for a region the server both reads and writes.
fn remote_access() -> u64 {
    u64::from(FI_REMOTE_READ | FI_REMOTE_WRITE | FI_READ | FI_WRITE)
}

/// A registered memory region, deregistered on drop or when revoked.
///
/// Holds a [`RdmaFabric`] so the domain cannot close while a registration against it is
/// still live.
///
/// Revoking deregisters the region early, from any thread, even while a transfer is
/// using it. That is how a transfer is cancelled: once `fi_close` returns, a peer can
/// no longer reach the memory, so an operation the server posts, or has in flight,
/// fails at the provider instead of landing.
#[derive(Debug)]
pub(crate) struct Registration {
    state: Arc<RegistrationState>,
    remote_key: u64,
}

#[derive(Debug)]
struct RegistrationState {
    /// The open region, or `None` once it has been closed.
    memory_region: Mutex<Option<NonNull<fid_mr>>>,
    /// Becomes `true` when the region is revoked for transfers waiting on it.
    revoked: watch::Sender<bool>,
    fabric: RdmaFabric,
}

// SAFETY: the region pointer is only dereferenced under `memory_region`'s lock, to
// close it once, and `fi_close` also takes the domain lock, as `fi_mr_reg` did. So no
// two threads ever use the pointer at once, and the close cannot overlap any other
// call into the domain.
unsafe impl Send for RegistrationState {}
unsafe impl Sync for RegistrationState {}

impl RegistrationState {
    fn revoke(&self) -> Result<(), RdmaError> {
        let mut memory_region = self
            .memory_region
            .lock()
            .unwrap_or_else(PoisonError::into_inner);
        let Some(open) = *memory_region else {
            return Ok(());
        };
        // SAFETY: the region is open, and the lock above keeps any other thread from
        // closing it while this one does.
        let fid = unsafe { &raw mut (*open.as_ptr()).fid };
        // A failed close leaves the region open, so it stays recorded as open: a later
        // revoke, or the drop, tries again.
        check(self.fabric.fi_close(fid), "fi_close")?;
        *memory_region = None;
        self.revoked.send_replace(true);
        Ok(())
    }
}

impl Registration {
    pub(crate) fn new(memory_region: *mut fid_mr, fabric: RdmaFabric) -> Self {
        let (revoked, _) = watch::channel(false);
        Self {
            // SAFETY: a region returned by a successful fi_mr_reg.
            remote_key: unsafe { fi_mr_key(memory_region) },
            state: Arc::new(RegistrationState {
                memory_region: Mutex::new(NonNull::new(memory_region)),
                revoked,
                fabric,
            }),
        }
    }

    /// The key a peer presents to reach this region.
    pub(crate) fn remote_key(&self) -> u64 {
        self.remote_key
    }

    /// Close the region now. A no-op once it is closed.
    pub(crate) fn revoke(&self) -> Result<(), RdmaError> {
        self.state.revoke()
    }

    /// Whether the region has been closed.
    pub(crate) fn is_revoked(&self) -> bool {
        *self.state.revoked.borrow()
    }

    /// Resolves once the region is closed. Owns what it needs, so it can be awaited
    /// while a transfer holds the buffer.
    pub(crate) fn revoked(&self) -> impl Future<Output = ()> + Send + 'static {
        let mut revoked = self.state.revoked.subscribe();
        async move {
            // An error means the registration was dropped, which closed the region.
            let _ = revoked.wait_for(|revoked| *revoked).await;
        }
    }

    /// A handle that closes the region from elsewhere without keeping it alive.
    pub(crate) fn handle(&self) -> RevokeHandle {
        RevokeHandle(Arc::downgrade(&self.state))
    }
}

impl Drop for Registration {
    /// Close the region before the memory it covers is freed.
    ///
    /// If a revoke is under way, this waits for it to finish.
    fn drop(&mut self) {
        let _ = self.state.revoke();
    }
}

/// Closes a [`Registration`]'s region from anywhere, without keeping it registered.
///
/// Weak so that a forgotten handle can never keep memory registered after the buffer
/// that owns it has been freed.
#[derive(Debug, Clone)]
pub(crate) struct RevokeHandle(Weak<RegistrationState>);

impl RevokeHandle {
    /// Close the region if it is still open. A no-op once it is closed or dropped.
    pub(crate) fn revoke(&self) -> Result<(), RdmaError> {
        match self.0.upgrade() {
            Some(state) => state.revoke(),
            None => Ok(()),
        }
    }

    /// Whether the region is closed, or the registration is gone altogether.
    pub(crate) fn is_released(&self) -> bool {
        self.0.upgrade().is_none_or(|state| *state.revoked.borrow())
    }
}

/// A `FI_EP_RDM` endpoint owning its fabric, domain, address vector and completion
/// queue.
///
/// Nothing here is a connection: `FI_EP_RDM` is a reliable *datagram* endpoint, so
/// opening one is local bring-up that contacts no server.
#[derive(Debug)]
pub(crate) struct LibfabricEndpoint {
    endpoint: *mut fid_ep,
    completion_queue: *mut fid_cq,
    address_vector: *mut fid_av,
    domain: *mut fid_domain,
    fabric: *mut fid_fabric,
    info: *mut fi_info,
    /// `FI_MR_VIRT_ADDR`, as efa does: an RMA initiator targets virtual addresses
    /// rather than offsets into the region.
    uses_virtual_addressing: bool,
    remote_keys: RKeySource,
}

// SAFETY: libfabric permits its objects to move between threads. Callers keep the
// endpoint behind a lock, so calls through these handles are exclusive of each
// other — with one exception: the progress poller reads the completion queue
// without that lock. That is why `query_info` asks for FI_THREAD_SAFE from any
// provider that gets a poller, and fails with that named as the reason when no
// provider will offer it.
unsafe impl Send for LibfabricEndpoint {}

impl LibfabricEndpoint {
    /// Bring up a local endpoint: select a domain, then fabric -> domain -> address
    /// vector -> completion queue -> endpoint -> bind -> enable.
    pub(crate) fn open(config: &FabricConfig) -> Result<Self, RdmaError> {
        let mut endpoint = LibfabricEndpoint {
            endpoint: ptr::null_mut(),
            completion_queue: ptr::null_mut(),
            address_vector: ptr::null_mut(),
            domain: ptr::null_mut(),
            fabric: ptr::null_mut(),
            info: ptr::null_mut(),
            uses_virtual_addressing: false,
            remote_keys: RKeySource::ProviderSelected,
        };

        let list = query_info(config)?;
        // SAFETY: `list` is a valid fi_info list, freed on every path below. Each
        // `check`ed call either fills its out-parameter or leaves it null, and Drop
        // closes whatever was opened.
        unsafe {
            let wanted = config.interface();
            let chosen = match wanted {
                Some(name) => select_domain(list, name),
                None => list,
            };
            if chosen.is_null() {
                let discovered = domain_names(list);
                fi_freeinfo(list);
                return Err(RdmaError::Configuration(format!(
                    "no fabric domain named {:?}; this host has {:?}",
                    wanted.unwrap_or_default(),
                    discovered
                )));
            }
            // Copy the chosen domain out standalone, so nothing downstream selects again.
            endpoint.info = fi_dupinfo(chosen);
            fi_freeinfo(list);
            if endpoint.info.is_null() {
                return Err(RdmaError::Fabric {
                    operation: "fi_dupinfo",
                    message: "returned null".into(),
                    errno: None,
                });
            }

            let mr_mode = domain_attr(endpoint.info)
                .ok_or_else(|| RdmaError::Fabric {
                    operation: "fi_dupinfo",
                    message: "returned an entry without domain attributes".into(),
                    errno: None,
                })?
                .mr_mode as u32;
            endpoint.uses_virtual_addressing = mr_mode & FI_MR_VIRT_ADDR != 0;
            endpoint.remote_keys = if mr_mode & FI_MR_PROV_KEY != 0 {
                RKeySource::ProviderSelected
            } else {
                RKeySource::ApplicationSelected { next_remote_key: 1 }
            };

            check(
                fi_fabric(
                    (*endpoint.info).fabric_attr,
                    &mut endpoint.fabric,
                    ptr::null_mut(),
                ),
                "fi_fabric",
            )?;
            check(
                fi_domain(
                    endpoint.fabric,
                    endpoint.info,
                    &mut endpoint.domain,
                    ptr::null_mut(),
                ),
                "fi_domain",
            )?;

            let mut av_attr: fi_av_attr = std::mem::zeroed();
            av_attr.type_ = fi_av_type_FI_AV_MAP;
            check(
                fi_av_open(
                    endpoint.domain,
                    &mut av_attr,
                    &mut endpoint.address_vector,
                    ptr::null_mut(),
                ),
                "fi_av_open",
            )?;

            let mut cq_attr: fi_cq_attr = std::mem::zeroed();
            cq_attr.format = fi_cq_format_FI_CQ_FORMAT_CONTEXT;
            check(
                fi_cq_open(
                    endpoint.domain,
                    &mut cq_attr,
                    &mut endpoint.completion_queue,
                    ptr::null_mut(),
                ),
                "fi_cq_open",
            )?;

            check(
                fi_endpoint(
                    endpoint.domain,
                    endpoint.info,
                    &mut endpoint.endpoint,
                    ptr::null_mut(),
                ),
                "fi_endpoint",
            )?;
            check(
                fi_ep_bind(
                    endpoint.endpoint,
                    &mut opened(endpoint.address_vector, "fi_av_open")?.fid,
                    0,
                ),
                "fi_ep_bind(av)",
            )?;
            check(
                fi_ep_bind(
                    endpoint.endpoint,
                    &mut opened(endpoint.completion_queue, "fi_cq_open")?.fid,
                    u64::from(FI_TRANSMIT | FI_RECV),
                ),
                "fi_ep_bind(cq)",
            )?;
            check(fi_enable(endpoint.endpoint), "fi_enable")?;
        }

        Ok(endpoint)
    }

    pub(crate) fn completion_queue(&self) -> *mut fid_cq {
        self.completion_queue
    }

    pub(crate) fn uses_virtual_addressing(&self) -> bool {
        self.uses_virtual_addressing
    }

    /// The endpoint's local fabric address, to advertise to the server.
    pub(crate) fn local_address(&self) -> Result<Vec<u8>, RdmaError> {
        let mut length: usize = 0;
        // The first call discovers the length, returning -FI_ETOOSMALL, which is why
        // its return code is deliberately not checked.
        // SAFETY: a null buffer with a zero length is how libfabric is asked for the size.
        unsafe {
            fi_getname(&mut (*self.endpoint).fid, ptr::null_mut(), &mut length);
        }
        if length == 0 {
            return Err(RdmaError::Fabric {
                operation: "fi_getname",
                message: "returned a zero-length address".into(),
                errno: None,
            });
        }
        let mut address = vec![0u8; length];
        check(
            // SAFETY: `address` has room for `length` bytes.
            unsafe {
                fi_getname(
                    &mut (*self.endpoint).fid,
                    address.as_mut_ptr().cast(),
                    &mut length,
                )
            },
            "fi_getname",
        )?;
        address.truncate(length);
        Ok(address)
    }

    /// Register host memory for remote RMA access.
    ///
    /// # Safety
    /// `buffer` must stay allocated and unmoved until the region is closed.
    pub(crate) unsafe fn register_remote(
        &mut self,
        buffer: &[u8],
    ) -> Result<*mut fid_mr, RdmaError> {
        unsafe { self.register(buffer, remote_access()) }
    }

    /// # Safety
    /// `buffer` must stay allocated and unmoved until the region is closed.
    unsafe fn register(&mut self, buffer: &[u8], access: u64) -> Result<*mut fid_mr, RdmaError> {
        let requested_key = self.remote_keys.take();
        let mut memory_region: *mut fid_mr = ptr::null_mut();
        check(
            // SAFETY: the caller guarantees `buffer` outlives the registration.
            unsafe {
                fi_mr_reg(
                    self.domain,
                    buffer.as_ptr().cast(),
                    buffer.len(),
                    access,
                    0,
                    requested_key,
                    0,
                    &mut memory_region,
                    ptr::null_mut(),
                )
            },
            "fi_mr_reg",
        )?;
        Ok(memory_region)
    }

    /// Insert a peer's fabric address into the address vector.
    ///
    /// efa-direct requires a target to hold the initiator's address before any RMA, so
    /// every address a handshake returned must be inserted before the first transfer.
    pub(crate) fn fi_av_insert(&mut self, peer_address: &[u8]) -> Result<fi_addr_t, RdmaError> {
        let mut peer: fi_addr_t = 0;
        // SAFETY: inserting one address from a caller-owned slice.
        let inserted = unsafe {
            fi_av_insert(
                self.address_vector,
                peer_address.as_ptr().cast(),
                1,
                &mut peer,
                0,
                ptr::null_mut(),
            )
        };
        if inserted < 0 {
            check(inserted, "fi_av_insert")?;
        }
        if inserted != 1 {
            return Err(RdmaError::Fabric {
                operation: "fi_av_insert",
                message: format!("inserted {inserted} of 1 addresses"),
                errno: None,
            });
        }
        Ok(peer)
    }

    /// Drop a peer handle [`Self::fi_av_insert`] minted so the server can no longer
    /// reach this client's memory through the removed entry.
    pub(crate) fn fi_av_remove(&mut self, peer: fi_addr_t) -> Result<(), RdmaError> {
        let mut peer = peer;
        // SAFETY: removing one handle this address vector minted.
        let status = unsafe { fi_av_remove(self.address_vector, &mut peer, 1, 0) };
        if status != 0 {
            return Err(RdmaError::Fabric {
                operation: "fi_av_remove",
                message: "address vector rejected the handle".to_string(),
                errno: Some(status),
            });
        }
        Ok(())
    }
}

impl Drop for LibfabricEndpoint {
    /// Close every fid in reverse construction order: an object cannot outlive the
    /// domain or fabric it was opened on.
    fn drop(&mut self) {
        // SAFETY: each handle is either null or was opened by `open` and has not been
        // closed before now.
        unsafe {
            if !self.endpoint.is_null() {
                fi_close(&mut (*self.endpoint).fid);
            }
            if !self.completion_queue.is_null() {
                fi_close(&mut (*self.completion_queue).fid);
            }
            if !self.address_vector.is_null() {
                fi_close(&mut (*self.address_vector).fid);
            }
            if !self.domain.is_null() {
                fi_close(&mut (*self.domain).fid);
            }
            if !self.fabric.is_null() {
                fi_close(&mut (*self.fabric).fid);
            }
            if !self.info.is_null() {
                fi_freeinfo(self.info);
            }
        }
    }
}

#[cfg(test)]
pub(crate) mod tests {
    use super::{
        LibfabricEndpoint, check, domain_attr, domain_names, query_info, query_info_requiring,
        select_domain,
    };
    use crate::config::{FabricConfig, Provider};
    use crate::error::RdmaError;
    use ofi_libfabric_sys::bindgen::{
        fi_cq_entry, fi_cq_read, fi_domain_attr, fi_freeinfo, fi_info, fi_mr_desc, fi_read,
        fi_threading_FI_THREAD_FID, fi_threading_FI_THREAD_SAFE, fid_mr,
    };
    use std::ptr;
    use std::time::{Duration, Instant};

    /// Post a read from `endpoint`'s own address under a remote key that no region
    /// has, so that it completes with an error entry on the completion queue. Returns
    /// the region registered for the read's destination, for the caller to close.
    ///
    /// # Safety
    /// `into` must stay allocated and unmoved until the returned region is closed.
    pub(crate) unsafe fn post_failing_read(
        endpoint: &mut LibfabricEndpoint,
        into: &[u8],
    ) -> *mut fid_mr {
        let own_address = endpoint
            .local_address()
            .expect("an endpoint has an address");
        let peer = endpoint
            .fi_av_insert(&own_address)
            .expect("its own address inserts");
        // SAFETY: the caller keeps `into` alive until the region is closed.
        let region = unsafe { endpoint.register_remote(into) }.expect("the destination registers");
        let deadline = Instant::now() + Duration::from_secs(5);
        loop {
            // SAFETY: `into` is registered as `region`, and every handle is open.
            let posted = unsafe {
                fi_read(
                    endpoint.endpoint,
                    into.as_ptr().cast_mut().cast(),
                    into.len(),
                    fi_mr_desc(region),
                    peer,
                    0,
                    u64::MAX,
                    ptr::null_mut(),
                )
            };
            if posted == 0 {
                return region;
            }
            // tcp connects on first use and asks for a retry until it has. Reading the
            // queue is what moves the connection along.
            assert_eq!(posted, -(libc::EAGAIN as isize), "fi_read failed");
            assert!(Instant::now() < deadline, "fi_read never posted");
            let mut entry = fi_cq_entry {
                op_context: ptr::null_mut(),
            };
            // SAFETY: the queue is open, and `entry` has room for one entry.
            unsafe {
                fi_cq_read(
                    endpoint.completion_queue,
                    ptr::from_mut(&mut entry).cast(),
                    1,
                )
            };
        }
    }

    #[test]
    fn an_address_that_names_nothing_is_not_inserted() {
        let mut endpoint = LibfabricEndpoint::open(&FabricConfig::new(Provider::Tcp))
            .expect("the tcp provider should open");
        let length = endpoint.local_address().expect("has an address").len();
        let error = endpoint
            .fi_av_insert(&vec![0xff; length])
            .expect_err("nothing listens there");
        assert!(
            matches!(
                error,
                RdmaError::Fabric {
                    operation: "fi_av_insert",
                    ..
                }
            ),
            "got {error:?}"
        );
    }

    #[test]
    fn a_null_entry_has_no_domain_attributes() {
        // SAFETY: null is allowed.
        assert!(unsafe { domain_attr(ptr::null()) }.is_none());
    }

    #[test]
    fn an_entry_without_domain_attributes_is_skipped() {
        // SAFETY: all-zero is a valid `fi_domain_attr` and `fi_info`: null pointers and
        // zero counts.
        let mut attributes: fi_domain_attr = unsafe { std::mem::zeroed() };
        attributes.name = c"eth0".as_ptr().cast_mut();
        let mut named: fi_info = unsafe { std::mem::zeroed() };
        named.domain_attr = &raw mut attributes;
        let mut missing: fi_info = unsafe { std::mem::zeroed() };
        missing.next = &raw mut named;
        let list = &raw mut missing;

        // SAFETY: `missing` is live, and its `domain_attr` is null.
        assert!(unsafe { domain_attr(&raw const missing) }.is_none());
        assert_eq!(domain_names(list), ["eth0"]);
        assert_eq!(select_domain(list, "eth0"), &raw mut named);
        assert!(select_domain(list, "eth1").is_null());
    }

    #[test]
    fn a_failure_carries_its_errno_and_libfabric_message() {
        // Any negative code will do here; this one is -ENODATA on Linux.
        // What is under test is how check() reports a code, not the code.
        let error = check(-61, "fi_getinfo").unwrap_err();
        let RdmaError::Fabric {
            operation,
            message,
            errno,
        } = &error
        else {
            panic!("expected a fabric error, got {error:?}");
        };
        assert_eq!(*operation, "fi_getinfo");
        assert_eq!(*errno, Some(-61));
        assert!(!message.is_empty(), "fi_strerror produced nothing");
    }

    #[test]
    fn bind_is_rejected_on_efa_direct() {
        let config = FabricConfig::new(Provider::EfaDirect).with_bind("127.0.0.1");
        let error = query_info(&config).unwrap_err();
        assert!(
            matches!(&error, RdmaError::Configuration(message) if message.contains("efa-direct")),
            "expected a configuration error naming the provider, got {error:?}"
        );
    }

    #[test]
    fn bind_is_accepted_on_tcp() {
        let config = FabricConfig::new(Provider::Tcp).with_bind("127.0.0.1");
        match query_info(&config) {
            // SAFETY: query_info hands back an owned list on success.
            Ok(list) => unsafe { fi_freeinfo(list) },
            Err(error) => panic!("tcp with a bind address should query cleanly: {error:?}"),
        }
    }

    #[test]
    fn a_polled_provider_hands_back_a_thread_safe_domain() {
        // tcp gets a progress poller, so its domain has to tolerate that thread
        // sitting in fi_cq_read while another registers memory on the same
        // domain. query_info asks for that; this checks the provider agrees.
        let config = FabricConfig::new(Provider::Tcp);
        assert!(
            config.provider().needs_manual_progress(),
            "this test is about providers that are polled"
        );
        let list = query_info(&config).expect("tcp should be available");
        // SAFETY: an owned fi_info list from query_info, read once and freed here.
        let threading = unsafe { domain_attr(list) }
            .expect("libfabric fills in domain_attr")
            .threading;
        unsafe { fi_freeinfo(list) };
        assert_eq!(
            threading, fi_threading_FI_THREAD_SAFE,
            "a polled provider offered a domain that needs the caller to serialise"
        );
    }

    #[test]
    fn a_refused_threading_requirement_says_what_was_missing() {
        // FI_THREAD_FID is deprecated in libfabric 2.x, so no provider offers it.
        // That makes it a lever for the case this path exists to explain: asking
        // for a model nothing can meet. libfabric answers by leaving the provider
        // out of the list, which on its own is indistinguishable from the provider
        // not being installed, so the error has to name the real reason.
        let config = FabricConfig::new(Provider::Tcp);
        match query_info_requiring(&config, Some(fi_threading_FI_THREAD_FID)) {
            Ok(list) => {
                // SAFETY: an owned list from fi_getinfo.
                unsafe { fi_freeinfo(list) };
                panic!("expected FI_THREAD_FID to be unsupported; this host offers it");
            }
            Err(RdmaError::Configuration(message)) => {
                assert!(
                    message.contains("FI_THREAD_FID"),
                    "the message should name what was asked for, got: {message}"
                );
                assert!(
                    message.contains("FI_THREAD_SAFE"),
                    "the message should name what the provider does offer, got: {message}"
                );
            }
            Err(other) => panic!("expected a configuration error naming the model, got {other:?}"),
        }
    }

    #[test]
    fn an_unknown_interface_reports_what_exists() {
        let config = FabricConfig::new(Provider::Tcp).with_interface("definitely-not-a-card");
        let error = LibfabricEndpoint::open(&config).unwrap_err();
        let RdmaError::Configuration(message) = &error else {
            panic!("expected a configuration error, got {error:?}");
        };
        assert!(message.contains("definitely-not-a-card"), "{message}");
        assert!(
            message.contains("this host has"),
            "should list the real domains: {message}"
        );
    }
}
