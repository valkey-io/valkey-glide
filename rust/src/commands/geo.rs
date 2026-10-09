// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Geospatial types and commands.

#![allow(clippy::too_many_arguments)]

use crate::GlideError;
use crate::ValkeyResult;
use crate::cmd::cmd;
use crate::commands::options::{ExistenceCheck, OrderBy};
use crate::executor::CommandExecutor;
use crate::value::FromValkeyValue;
use crate::value::ValkeyValue;
use crate::value::to_glide_error;
use crate::write::ToSingleValkeyArg;
use crate::write::ToValkeyArgs;
use crate::write::ValkeyWrite;
use async_trait::async_trait;
use bytes::Bytes;

/// Distance unit for the geo commands.
///
/// Mirrors redis-rs's `geo::Unit` type.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum GeoUnit {
    /// Meters (`m`).
    Meters,
    /// Kilometers (`km`).
    Kilometers,
    /// Miles (`mi`).
    Miles,
    /// Feet (`ft`).
    Feet,
}

impl ToValkeyArgs for GeoUnit {
    fn write_valkey_args<W: ?Sized + ValkeyWrite>(&self, out: &mut W) {
        out.write_arg(match self {
            GeoUnit::Meters => b"m".as_slice(),
            GeoUnit::Kilometers => b"km".as_slice(),
            GeoUnit::Miles => b"mi".as_slice(),
            GeoUnit::Feet => b"ft".as_slice(),
        });
    }
}

impl ToSingleValkeyArg for GeoUnit {}

/// A longitude/latitude coordinate.
///
/// Mirrors redis-rs's `geo::Coord` type.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct GeoCoord<T> {
    /// Longitude.
    pub longitude: T,
    /// Latitude.
    pub latitude: T,
}

impl<T> GeoCoord<T> {
    /// Create a coordinate from a longitude and a latitude.
    pub fn lon_lat(longitude: T, latitude: T) -> Self {
        Self {
            longitude,
            latitude,
        }
    }
}

impl<T: ToValkeyArgs> ToValkeyArgs for GeoCoord<T> {
    fn write_valkey_args<W: ?Sized + ValkeyWrite>(&self, out: &mut W) {
        self.longitude.write_valkey_args(out);
        self.latitude.write_valkey_args(out);
    }
}

impl<T: FromValkeyValue> FromValkeyValue for GeoCoord<T> {
    fn from_owned_valkey_value(value: ValkeyValue) -> ValkeyResult<Self> {
        match value {
            ValkeyValue::Array(items) if items.len() == 2 => {
                let mut items = items.into_iter();
                let (Some(longitude), Some(latitude)) = (items.next(), items.next()) else {
                    unreachable!("checked length");
                };
                Ok(Self {
                    longitude: T::from_owned_valkey_value(longitude)?,
                    latitude: T::from_owned_valkey_value(latitude)?,
                })
            }
            other => Err(to_glide_error(other, "Expected a pair of numbers.")),
        }
    }
}

/// The search area shape for `GEOSEARCH`/`GEOSEARCHSTORE`.
///
/// Mirrors Python `GeoSearchByRadius`/`GeoSearchByBox`.
#[derive(Debug, Clone, Copy, PartialEq)]
pub enum GeoSearchShape {
    /// A circular area of the given radius (`BYRADIUS`).
    ByRadius {
        /// The radius.
        radius: f64,
        /// The distance unit.
        unit: GeoUnit,
    },
    /// A rectangular area of the given width and height (`BYBOX`).
    ByBox {
        /// The box width.
        width: f64,
        /// The box height.
        height: f64,
        /// The distance unit.
        unit: GeoUnit,
    },
}

impl ToValkeyArgs for GeoSearchShape {
    fn write_valkey_args<W: ?Sized + ValkeyWrite>(&self, out: &mut W) {
        match self {
            GeoSearchShape::ByRadius { radius, unit } => {
                out.write_arg(b"BYRADIUS");
                radius.write_valkey_args(out);
                unit.write_valkey_args(out);
            }
            GeoSearchShape::ByBox {
                width,
                height,
                unit,
            } => {
                out.write_arg(b"BYBOX");
                width.write_valkey_args(out);
                height.write_valkey_args(out);
                unit.write_valkey_args(out);
            }
        }
    }
}

/// Geospatial commands.
#[async_trait]
pub trait GeoCommands: CommandExecutor {
    /// Search a geospatial index by radius from a member (`GEOSEARCH ... FROMMEMBER ... BYRADIUS`).
    async fn geosearch_by_radius_from_member<K: ToValkeyArgs + Send, M: ToValkeyArgs + Send>(
        &self,
        key: K,
        member: M,
        radius: f64,
        unit: GeoUnit,
    ) -> ValkeyResult<Vec<Bytes>> {
        let cmd = cmd("GEOSEARCH")
            .with_arg(key)
            .with_arg("FROMMEMBER")
            .with_arg(member)
            .with_arg("BYRADIUS")
            .with_arg(radius)
            .with_arg(unit);
        match self.execute_command(cmd, None).await? {
            ValkeyValue::Array(items) => items
                .into_iter()
                .map(Bytes::from_owned_valkey_value)
                .collect(),
            ValkeyValue::Nil => Ok(Vec::new()),
            other => Ok(vec![Bytes::from_owned_valkey_value(other)?]),
        }
    }

    /// Add geospatial members with options (`GEOADD` with `NX`/`XX`/`CH`).
    /// Returns the number of added (or, with `changed`, changed) members.
    async fn geoadd_options<K: ToValkeyArgs + Send, M: ToValkeyArgs + Send + Sync>(
        &self,
        key: K,
        members: M,
        existence_check: Option<ExistenceCheck>,
        changed: bool,
    ) -> ValkeyResult<i64> {
        let cmd = cmd("GEOADD")
            .with_arg(key)
            .with_arg(existence_check)
            .with_arg(changed.then_some("CH"))
            .with_arg(members);
        i64::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Search a geospatial index from a member with a given shape.
    /// Returns the matching members.
    async fn geosearch_from_member<K: ToValkeyArgs + Send, M: ToValkeyArgs + Send>(
        &self,
        key: K,
        member: M,
        shape: GeoSearchShape,
        options: GeoSearchOptions,
    ) -> ValkeyResult<Vec<GeoSearchResult>> {
        let cmd = cmd("GEOSEARCH")
            .with_arg(key)
            .with_arg("FROMMEMBER")
            .with_arg(member)
            .with_arg(shape)
            .with_arg(options);
        Vec::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Search a geospatial index from a coordinate with a given shape.
    /// Returns the matching members.
    async fn geosearch_from_coord<K: ToValkeyArgs + Send>(
        &self,
        key: K,
        coord: GeoCoord<f64>,
        shape: GeoSearchShape,
        options: GeoSearchOptions,
    ) -> ValkeyResult<Vec<GeoSearchResult>> {
        let cmd = cmd("GEOSEARCH")
            .with_arg(key)
            .with_arg("FROMLONLAT")
            .with_arg(coord.longitude)
            .with_arg(coord.latitude)
            .with_arg(shape)
            .with_arg(options);
        Vec::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Search from a member and store the results into `destination`.
    /// Returns the number of members stored.
    async fn geosearchstore_from_member<
        D: ToValkeyArgs + Send,
        S: ToValkeyArgs + Send,
        M: ToValkeyArgs + Send,
    >(
        &self,
        destination: D,
        source: S,
        member: M,
        shape: GeoSearchShape,
        options: GeoSearchStoreOptions,
    ) -> ValkeyResult<i64> {
        let cmd = cmd("GEOSEARCHSTORE")
            .with_arg(destination)
            .with_arg(source)
            .with_arg("FROMMEMBER")
            .with_arg(member)
            .with_arg(shape)
            .with_arg(options);
        i64::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Search from a coordinate and store the results into `destination`.
    /// Returns the number of members stored.
    async fn geosearchstore_from_coord<D: ToValkeyArgs + Send, S: ToValkeyArgs + Send>(
        &self,
        destination: D,
        source: S,
        coord: GeoCoord<f64>,
        shape: GeoSearchShape,
        options: GeoSearchStoreOptions,
    ) -> ValkeyResult<i64> {
        let cmd = cmd("GEOSEARCHSTORE")
            .with_arg(destination)
            .with_arg(source)
            .with_arg("FROMLONLAT")
            .with_arg(coord.longitude)
            .with_arg(coord.latitude)
            .with_arg(shape)
            .with_arg(options);
        i64::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }
}

/// Options for `GEOSEARCH`.
#[derive(Debug, Clone, Copy, Default, PartialEq, Eq)]
pub struct GeoSearchOptions {
    /// The sort order for results (`ASC`/`DESC`), or `None` for unsorted results.
    pub order: Option<OrderBy>,
    /// The maximum number of results to return, or `None` for no limit (`COUNT`).
    pub count: Option<i64>,
    /// Whether to allow non-closest results (`ANY`). Requires `count`.
    pub any: bool,
    /// Whether to include each member's position (`WITHCOORD`).
    pub with_position: bool,
    /// Whether to include each member's distance from the search origin (`WITHDIST`).
    pub with_distance: bool,
    /// Whether to include each member's geohash (`WITHHASH`).
    pub with_hash: bool,
}

impl ToValkeyArgs for GeoSearchOptions {
    fn write_valkey_args<W: ?Sized + ValkeyWrite>(&self, out: &mut W) {
        write_order_count_any(out, self.order, self.count, self.any);
        if self.with_position {
            out.write_arg(b"WITHCOORD");
        }
        if self.with_distance {
            out.write_arg(b"WITHDIST");
        }
        if self.with_hash {
            out.write_arg(b"WITHHASH");
        }
    }
}

/// A matching member from a `GEOSEARCH` command.
#[derive(Debug, Clone, PartialEq)]
pub struct GeoSearchResult {
    /// The member name.
    pub member: Bytes,
    /// The member's position (`WITHCOORD`).
    pub position: Option<GeoCoord<f64>>,
    /// The member's distance from the search origin, in the shape's unit (`WITHDIST`).
    pub distance: Option<f64>,
    /// The member's geohash (`WITHHASH`).
    pub hash: Option<i64>,
}

impl FromValkeyValue for GeoSearchResult {
    fn from_owned_valkey_value(value: ValkeyValue) -> ValkeyResult<Self> {
        // Without `WITH*` flags, each result is a member name.
        if let ValkeyValue::BulkString(member) = value {
            return Ok(Self {
                member,
                position: None,
                distance: None,
                hash: None,
            });
        }

        let ValkeyValue::Array(items) = value else {
            return Err(to_glide_error(value, "Unexpected GEOSEARCH result."));
        };

        // With `WITH*` flags, each result is a `[member, [distance?, hash?, position?]]` array.
        if items.len() != 2 {
            return Err(to_glide_error(
                ValkeyValue::Array(items),
                "Unexpected GEOSEARCH result.",
            ));
        }
        let mut items = items.into_iter();
        let (Some(member), Some(ValkeyValue::Array(extras))) = (items.next(), items.next()) else {
            return Err(GlideError::Request("Unexpected GEOSEARCH result.".into()));
        };

        let mut result = Self {
            member: Bytes::from_owned_valkey_value(member)?,
            position: None,
            distance: None,
            hash: None,
        };

        // The extras have distinct reply types:
        //  - distance is a double
        //  - hash is an integer
        //  - position is an array
        for extra in extras {
            match extra {
                ValkeyValue::Int(hash) => result.hash = Some(hash),
                ValkeyValue::Array(_) => {
                    result.position = Some(GeoCoord::from_owned_valkey_value(extra)?)
                }
                distance => result.distance = Some(f64::from_owned_valkey_value(distance)?),
            }
        }

        Ok(result)
    }
}

/// Options for `GEOSEARCHSTORE`.
#[derive(Debug, Clone, Copy, Default, PartialEq, Eq)]
pub struct GeoSearchStoreOptions {
    /// The sort order for results (`ASC`/`DESC`), or `None` for unsorted results.
    pub order: Option<OrderBy>,
    /// The maximum number of results to store, or `None` for no limit (`COUNT`).
    pub count: Option<i64>,
    /// Whether to allow non-closest results (`ANY`). Requires `count`.
    pub any: bool,
    /// Whether to store distances as scores instead of geohash values (`STOREDIST`).
    pub store_dist: bool,
}

impl ToValkeyArgs for GeoSearchStoreOptions {
    fn write_valkey_args<W: ?Sized + ValkeyWrite>(&self, out: &mut W) {
        write_order_count_any(out, self.order, self.count, self.any);
        if self.store_dist {
            out.write_arg(b"STOREDIST");
        }
    }
}

/// Writes the `[ASC|DESC] [COUNT count [ANY]]` arguments shared by the geo search options.
fn write_order_count_any<W: ?Sized + ValkeyWrite>(
    out: &mut W,
    order: Option<OrderBy>,
    count: Option<i64>,
    any: bool,
) {
    if let Some(order) = order {
        out.write_arg(order.as_arg().as_bytes());
    }
    if let Some(count) = count {
        out.write_arg(b"COUNT");
        out.write_arg_fmt(count);
    }
    if any {
        out.write_arg(b"ANY");
    }
}

impl<T: CommandExecutor + ?Sized> GeoCommands for T {}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::test_utils::assert_args;
    use crate::test_utils::assert_args_empty;

    #[test]
    fn unit_args() {
        assert_args(GeoUnit::Meters, &["m"]);
        assert_args(GeoUnit::Kilometers, &["km"]);
        assert_args(GeoUnit::Miles, &["mi"]);
        assert_args(GeoUnit::Feet, &["ft"]);
    }

    #[test]
    fn coord_args() {
        assert_args(GeoCoord::lon_lat(13.5, 38.5), &["13.5", "38.5"]);
    }

    #[test]
    fn geosearch_shape_args() {
        assert_args(
            GeoSearchShape::ByRadius {
                radius: 5.0,
                unit: GeoUnit::Kilometers,
            },
            &["BYRADIUS", "5.0", "km"],
        );
        assert_args(
            GeoSearchShape::ByBox {
                width: 2.0,
                height: 3.0,
                unit: GeoUnit::Meters,
            },
            &["BYBOX", "2.0", "3.0", "m"],
        );
    }

    #[test]
    fn geosearch_options_args() {
        assert_args_empty(GeoSearchOptions::default());
        assert_args(
            GeoSearchOptions {
                order: Some(OrderBy::Asc),
                count: Some(10),
                any: true,
                ..Default::default()
            },
            &["ASC", "COUNT", "10", "ANY"],
        );
        assert_args(
            GeoSearchOptions {
                order: Some(OrderBy::Desc),
                count: Some(5),
                with_position: true,
                with_distance: true,
                with_hash: true,
                ..Default::default()
            },
            &["DESC", "COUNT", "5", "WITHCOORD", "WITHDIST", "WITHHASH"],
        );
    }

    #[test]
    fn geosearchstore_options_args() {
        assert_args_empty(GeoSearchStoreOptions::default());
        assert_args(
            GeoSearchStoreOptions {
                order: Some(OrderBy::Desc),
                count: Some(3),
                any: true,
                store_dist: true,
            },
            &["DESC", "COUNT", "3", "ANY", "STOREDIST"],
        );
    }

    #[test]
    fn coord_decoding() {
        let pos = ValkeyValue::Array(vec![
            ValkeyValue::BulkString(b"13.5".to_vec().into()),
            ValkeyValue::BulkString(b"38.5".to_vec().into()),
        ]);
        assert_eq!(
            GeoCoord::<f64>::from_owned_valkey_value(pos.clone()).unwrap(),
            GeoCoord::lon_lat(13.5, 38.5)
        );
        assert_eq!(
            GeoCoord::<String>::from_owned_valkey_value(pos).unwrap(),
            GeoCoord::lon_lat("13.5".to_string(), "38.5".to_string())
        );
        assert!(
            GeoCoord::<f64>::from_owned_valkey_value(ValkeyValue::Array(vec![ValkeyValue::Int(1)]))
                .is_err()
        );
    }

    #[test]
    fn geosearch_result_decoding() {
        let bulk = |s: &str| ValkeyValue::BulkString(s.as_bytes().to_vec().into());
        let member = |name: &str| GeoSearchResult {
            member: Bytes::from(name.to_string()),
            position: None,
            distance: None,
            hash: None,
        };

        assert_eq!(
            GeoSearchResult::from_owned_valkey_value(bulk("Palermo")).unwrap(),
            member("Palermo")
        );

        let all = ValkeyValue::Array(vec![
            bulk("Palermo"),
            ValkeyValue::Array(vec![
                ValkeyValue::Double(190.4424),
                ValkeyValue::Int(3479099956230698),
                ValkeyValue::Array(vec![ValkeyValue::Double(13.5), ValkeyValue::Double(38.5)]),
            ]),
        ]);
        assert_eq!(
            GeoSearchResult::from_owned_valkey_value(all).unwrap(),
            GeoSearchResult {
                position: Some(GeoCoord::lon_lat(13.5, 38.5)),
                distance: Some(190.4424),
                hash: Some(3479099956230698),
                ..member("Palermo")
            }
        );

        let hash_only = ValkeyValue::Array(vec![
            bulk("Catania"),
            ValkeyValue::Array(vec![ValkeyValue::Int(42)]),
        ]);
        assert_eq!(
            GeoSearchResult::from_owned_valkey_value(hash_only).unwrap(),
            GeoSearchResult {
                hash: Some(42),
                ..member("Catania")
            }
        );

        let strings = ValkeyValue::Array(vec![
            bulk("Palermo"),
            ValkeyValue::Array(vec![
                bulk("190.4424"),
                ValkeyValue::Array(vec![bulk("13.5"), bulk("38.5")]),
            ]),
        ]);
        assert_eq!(
            GeoSearchResult::from_owned_valkey_value(strings).unwrap(),
            GeoSearchResult {
                position: Some(GeoCoord::lon_lat(13.5, 38.5)),
                distance: Some(190.4424),
                ..member("Palermo")
            }
        );

        let position_only = ValkeyValue::Array(vec![
            bulk("Catania"),
            ValkeyValue::Array(vec![ValkeyValue::Array(vec![
                ValkeyValue::Double(15.0),
                ValkeyValue::Double(37.5),
            ])]),
        ]);
        assert_eq!(
            GeoSearchResult::from_owned_valkey_value(position_only).unwrap(),
            GeoSearchResult {
                position: Some(GeoCoord::lon_lat(15.0, 37.5)),
                ..member("Catania")
            }
        );

        assert!(GeoSearchResult::from_owned_valkey_value(ValkeyValue::Int(1)).is_err());
        assert!(GeoSearchResult::from_owned_valkey_value(ValkeyValue::Array(vec![])).is_err());
        assert!(
            GeoSearchResult::from_owned_valkey_value(ValkeyValue::Array(vec![
                bulk("Palermo"),
                ValkeyValue::Array(vec![]),
                ValkeyValue::Int(1),
            ]))
            .is_err()
        );
        assert!(
            GeoSearchResult::from_owned_valkey_value(ValkeyValue::Array(vec![
                bulk("Palermo"),
                ValkeyValue::Int(1),
            ]))
            .is_err()
        );
    }
}
