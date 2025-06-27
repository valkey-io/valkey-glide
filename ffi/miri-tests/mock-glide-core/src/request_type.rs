use redis::Cmd;

#[derive(Debug, Clone, Copy)]
#[repr(C)]
pub struct RequestType;

impl RequestType {
    pub fn get_command(&self) -> Option<Cmd> {
        Some(Cmd)
    }
}
