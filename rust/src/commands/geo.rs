// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Geospatial commands. Mirrors Python's geo command surface.
#![allow(clippy::too_many_arguments)]

use crate::ValkeyResult;
use crate::cmd::Cmd;
use crate::commands::options::{ConditionalChange, OrderBy};
use crate::executor::CommandExecutor;
use crate::value::FromValkeyValue;
use crate::value::ToValkeyArgs;
use crate::value::ValkeyValue;
use async_trait::async_trait;
use bytes::Bytes;

/// Distance unit for geo commands.
///
/// Mirrors Python `GeoUnit`.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum GeoUnit {
    /// Meters.
    Meters,
    /// Kilometers.
    Kilometers,
    /// Miles.
    Miles,
    /// Feet.
    Feet,
}

impl GeoUnit {
    fn as_arg(&self) -> &'static str {
        match self {
            GeoUnit::Meters => "m",
            GeoUnit::Kilometers => "km",
            GeoUnit::Miles => "mi",
            GeoUnit::Feet => "ft",
        }
    }
}

/// A longitude/latitude pair.
///
/// Mirrors Python `GeospatialData`.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct GeospatialData {
    /// Longitude.
    pub longitude: f64,
    /// Latitude.
    pub latitude: f64,
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

impl GeoSearchShape {
    fn add_to(&self, cmd: &mut Cmd) {
        match self {
            GeoSearchShape::ByRadius { radius, unit } => {
                cmd.arg("BYRADIUS").arg(radius).arg(unit.as_arg());
            }
            GeoSearchShape::ByBox {
                width,
                height,
                unit,
            } => {
                cmd.arg("BYBOX").arg(width).arg(height).arg(unit.as_arg());
            }
        }
    }
}

/// Geospatial commands (`GEOADD`, `GEOPOS`, `GEODIST`, `GEOHASH`, `GEOSEARCH`).
#[async_trait]
pub trait GeoCommands: CommandExecutor {
    /// Add geospatial members to `key` (`GEOADD`); returns members added.
    async fn geoadd<K: ToValkeyArgs + Send, M: ToValkeyArgs + Send + Sync>(
        &self,
        key: K,
        members_positions: &[(M, GeospatialData)],
    ) -> ValkeyResult<i64> {
        let mut cmd = Cmd::new();
        cmd.arg("GEOADD").arg(key);
        for (m, pos) in members_positions {
            cmd.arg(pos.longitude).arg(pos.latitude).arg(m);
        }
        i64::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Get the distance between two members (`GEODIST`).
    async fn geodist<K: ToValkeyArgs + Send, M1: ToValkeyArgs + Send, M2: ToValkeyArgs + Send>(
        &self,
        key: K,
        member1: M1,
        member2: M2,
        unit: Option<GeoUnit>,
    ) -> ValkeyResult<Option<f64>> {
        let mut cmd = Cmd::new();
        cmd.arg("GEODIST").arg(key).arg(member1).arg(member2);
        if let Some(u) = unit {
            cmd.arg(u.as_arg());
        }
        Option::<f64>::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Get the geohash strings of members (`GEOHASH`).
    async fn geohash<K: ToValkeyArgs + Send, M: ToValkeyArgs + Send + Sync>(
        &self,
        key: K,
        members: &[M],
    ) -> ValkeyResult<Vec<Option<Bytes>>> {
        let mut cmd = Cmd::new();
        cmd.arg("GEOHASH").arg(key);
        for m in members {
            cmd.arg(m);
        }
        match self.execute_command(cmd, None).await? {
            ValkeyValue::Array(items) => items
                .into_iter()
                .map(Option::<Bytes>::from_owned_valkey_value)
                .collect(),
            other => Ok(vec![Option::<Bytes>::from_owned_valkey_value(other)?]),
        }
    }

    /// Get the positions (longitude, latitude) of members (`GEOPOS`).
    async fn geopos<K: ToValkeyArgs + Send, M: ToValkeyArgs + Send + Sync>(
        &self,
        key: K,
        members: &[M],
    ) -> ValkeyResult<Vec<Option<(f64, f64)>>> {
        let mut cmd = Cmd::new();
        cmd.arg("GEOPOS").arg(key);
        for m in members {
            cmd.arg(m);
        }
        match self.execute_command(cmd, None).await? {
            ValkeyValue::Array(items) => {
                let mut out = Vec::with_capacity(items.len());
                for it in items {
                    match it {
                        ValkeyValue::Nil => out.push(None),
                        ValkeyValue::Array(mut pair) if pair.len() == 2 => {
                            let lat = f64::from_owned_valkey_value(pair.pop().unwrap())?;
                            let lon = f64::from_owned_valkey_value(pair.pop().unwrap())?;
                            out.push(Some((lon, lat)));
                        }
                        _ => out.push(None),
                    }
                }
                Ok(out)
            }
            _ => Ok(Vec::new()),
        }
    }

    /// Search a geospatial index by radius from a member (`GEOSEARCH ... FROMMEMBER ... BYRADIUS`).
    async fn geosearch_by_radius_from_member<K: ToValkeyArgs + Send, M: ToValkeyArgs + Send>(
        &self,
        key: K,
        member: M,
        radius: f64,
        unit: GeoUnit,
    ) -> ValkeyResult<Vec<Bytes>> {
        let mut cmd = Cmd::new();
        cmd.arg("GEOSEARCH")
            .arg(key)
            .arg("FROMMEMBER")
            .arg(member)
            .arg("BYRADIUS")
            .arg(radius)
            .arg(unit.as_arg());
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
        members_positions: &[(M, GeospatialData)],
        conditional_change: Option<ConditionalChange>,
        changed: bool,
    ) -> ValkeyResult<i64> {
        let mut cmd = Cmd::new();
        cmd.arg("GEOADD").arg(key);
        if let Some(c) = conditional_change {
            c.add_to(&mut cmd);
        }
        if changed {
            cmd.arg("CH");
        }
        for (m, pos) in members_positions {
            cmd.arg(pos.longitude).arg(pos.latitude).arg(m);
        }
        i64::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Search a geospatial index from a member with a given shape (`GEOSEARCH
    /// ... FROMMEMBER ... BYRADIUS|BYBOX`). Returns matching member names.
    async fn geosearch_from_member<K: ToValkeyArgs + Send, M: ToValkeyArgs + Send>(
        &self,
        key: K,
        member: M,
        shape: GeoSearchShape,
        order: Option<OrderBy>,
        count: Option<i64>,
        any: bool,
    ) -> ValkeyResult<Vec<Bytes>> {
        let mut cmd = Cmd::new();
        cmd.arg("GEOSEARCH").arg(key).arg("FROMMEMBER").arg(member);
        shape.add_to(&mut cmd);
        add_search_tail(&mut cmd, order, count, any);
        collect_bytes(self.execute_command(cmd, None).await?)
    }

    /// Search a geospatial index from a coordinate with a given shape
    /// (`GEOSEARCH ... FROMLONLAT ... BYRADIUS|BYBOX`).
    async fn geosearch_from_coord<K: ToValkeyArgs + Send>(
        &self,
        key: K,
        coord: GeospatialData,
        shape: GeoSearchShape,
        order: Option<OrderBy>,
        count: Option<i64>,
        any: bool,
    ) -> ValkeyResult<Vec<Bytes>> {
        let mut cmd = Cmd::new();
        cmd.arg("GEOSEARCH")
            .arg(key)
            .arg("FROMLONLAT")
            .arg(coord.longitude)
            .arg(coord.latitude);
        shape.add_to(&mut cmd);
        add_search_tail(&mut cmd, order, count, any);
        collect_bytes(self.execute_command(cmd, None).await?)
    }

    /// Search from a member and store the results into `destination`
    /// (`GEOSEARCHSTORE ... FROMMEMBER`). Returns the number stored.
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
        order: Option<OrderBy>,
        count: Option<i64>,
        any: bool,
        store_dist: bool,
    ) -> ValkeyResult<i64> {
        let mut cmd = Cmd::new();
        cmd.arg("GEOSEARCHSTORE")
            .arg(destination)
            .arg(source)
            .arg("FROMMEMBER")
            .arg(member);
        shape.add_to(&mut cmd);
        add_search_tail(&mut cmd, order, count, any);
        if store_dist {
            cmd.arg("STOREDIST");
        }
        i64::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Search from a coordinate and store the results into `destination`
    /// (`GEOSEARCHSTORE ... FROMLONLAT`).
    async fn geosearchstore_from_coord<D: ToValkeyArgs + Send, S: ToValkeyArgs + Send>(
        &self,
        destination: D,
        source: S,
        coord: GeospatialData,
        shape: GeoSearchShape,
        order: Option<OrderBy>,
        count: Option<i64>,
        any: bool,
        store_dist: bool,
    ) -> ValkeyResult<i64> {
        let mut cmd = Cmd::new();
        cmd.arg("GEOSEARCHSTORE")
            .arg(destination)
            .arg(source)
            .arg("FROMLONLAT")
            .arg(coord.longitude)
            .arg(coord.latitude);
        shape.add_to(&mut cmd);
        add_search_tail(&mut cmd, order, count, any);
        if store_dist {
            cmd.arg("STOREDIST");
        }
        i64::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }
}

/// Append the common `[ASC|DESC] [COUNT count [ANY]]` tail to a geo search.
fn add_search_tail(cmd: &mut Cmd, order: Option<OrderBy>, count: Option<i64>, any: bool) {
    if let Some(o) = order {
        cmd.arg(o.as_arg());
    }
    if let Some(c) = count {
        cmd.arg("COUNT").arg(c);
        if any {
            cmd.arg("ANY");
        }
    }
}

fn collect_bytes(v: ValkeyValue) -> ValkeyResult<Vec<Bytes>> {
    match v {
        ValkeyValue::Array(items) => items
            .into_iter()
            .map(Bytes::from_owned_valkey_value)
            .collect(),
        ValkeyValue::Nil => Ok(Vec::new()),
        other => Ok(vec![Bytes::from_owned_valkey_value(other)?]),
    }
}

impl<T: CommandExecutor + ?Sized> GeoCommands for T {}

#[cfg(test)]
mod tests {
    use super::*;

    fn args_of(cmd: &Cmd) -> Vec<String> {
        cmd.as_redis()
            .args_iter()
            .filter_map(|a| match a {
                redis::Arg::Simple(bytes) => Some(String::from_utf8_lossy(bytes).into_owned()),
                redis::Arg::Cursor => None,
            })
            .collect()
    }

    #[test]
    fn geo_unit_args() {
        assert_eq!(GeoUnit::Meters.as_arg(), "m");
        assert_eq!(GeoUnit::Kilometers.as_arg(), "km");
        assert_eq!(GeoUnit::Miles.as_arg(), "mi");
        assert_eq!(GeoUnit::Feet.as_arg(), "ft");
    }

    #[test]
    fn geosearch_shape_args() {
        let mut cmd = Cmd::new();
        GeoSearchShape::ByRadius {
            radius: 5.0,
            unit: GeoUnit::Kilometers,
        }
        .add_to(&mut cmd);
        assert_eq!(args_of(&cmd), vec!["BYRADIUS", "5.0", "km"]);

        let mut cmd = Cmd::new();
        GeoSearchShape::ByBox {
            width: 2.0,
            height: 3.0,
            unit: GeoUnit::Meters,
        }
        .add_to(&mut cmd);
        assert_eq!(args_of(&cmd), vec!["BYBOX", "2.0", "3.0", "m"]);
    }

    #[test]
    fn search_tail_args() {
        let mut cmd = Cmd::new();
        add_search_tail(&mut cmd, Some(OrderBy::Asc), Some(10), true);
        assert_eq!(args_of(&cmd), vec!["ASC", "COUNT", "10", "ANY"]);
    }
}
