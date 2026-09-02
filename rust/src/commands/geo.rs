// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Geospatial commands. Mirrors Python's geo command surface.
#![allow(clippy::too_many_arguments)]

use crate::commands::options::{ConditionalChange, OrderBy};
use crate::error::Result;
use crate::executor::CommandExecutor;
use crate::value;
use async_trait::async_trait;
use bytes::Bytes;
use redis::{Cmd, ToRedisArgs};

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
    async fn geoadd<K: ToRedisArgs + Send, M: ToRedisArgs + Send + Sync>(
        &self,
        key: K,
        members_positions: &[(M, GeospatialData)],
    ) -> Result<i64> {
        let mut cmd = Cmd::new();
        cmd.arg("GEOADD").arg(key);
        for (m, pos) in members_positions {
            cmd.arg(pos.longitude).arg(pos.latitude).arg(m);
        }
        value::to_i64(self.execute_command(cmd, None).await?)
    }

    /// Get the distance between two members (`GEODIST`).
    async fn geodist<K: ToRedisArgs + Send, M1: ToRedisArgs + Send, M2: ToRedisArgs + Send>(
        &self,
        key: K,
        member1: M1,
        member2: M2,
        unit: Option<GeoUnit>,
    ) -> Result<Option<f64>> {
        let mut cmd = Cmd::new();
        cmd.arg("GEODIST").arg(key).arg(member1).arg(member2);
        if let Some(u) = unit {
            cmd.arg(u.as_arg());
        }
        value::to_opt_f64(self.execute_command(cmd, None).await?)
    }

    /// Get the geohash strings of members (`GEOHASH`).
    async fn geohash<K: ToRedisArgs + Send, M: ToRedisArgs + Send + Sync>(
        &self,
        key: K,
        members: &[M],
    ) -> Result<Vec<Option<Bytes>>> {
        let mut cmd = Cmd::new();
        cmd.arg("GEOHASH").arg(key);
        for m in members {
            cmd.arg(m);
        }
        match self.execute_command(cmd, None).await? {
            redis::Value::Array(items) => items.into_iter().map(value::to_opt_bytes).collect(),
            other => Ok(vec![value::to_opt_bytes(other)?]),
        }
    }

    /// Get the positions (longitude, latitude) of members (`GEOPOS`).
    async fn geopos<K: ToRedisArgs + Send, M: ToRedisArgs + Send + Sync>(
        &self,
        key: K,
        members: &[M],
    ) -> Result<Vec<Option<(f64, f64)>>> {
        let mut cmd = Cmd::new();
        cmd.arg("GEOPOS").arg(key);
        for m in members {
            cmd.arg(m);
        }
        match self.execute_command(cmd, None).await? {
            redis::Value::Array(items) => {
                let mut out = Vec::with_capacity(items.len());
                for it in items {
                    match it {
                        redis::Value::Nil => out.push(None),
                        redis::Value::Array(mut pair) if pair.len() == 2 => {
                            let lat = value::to_f64(pair.pop().unwrap())?;
                            let lon = value::to_f64(pair.pop().unwrap())?;
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
    async fn geosearch_by_radius_from_member<K: ToRedisArgs + Send, M: ToRedisArgs + Send>(
        &self,
        key: K,
        member: M,
        radius: f64,
        unit: GeoUnit,
    ) -> Result<Vec<Bytes>> {
        let mut cmd = Cmd::new();
        cmd.arg("GEOSEARCH")
            .arg(key)
            .arg("FROMMEMBER")
            .arg(member)
            .arg("BYRADIUS")
            .arg(radius)
            .arg(unit.as_arg());
        match self.execute_command(cmd, None).await? {
            redis::Value::Array(items) => items.into_iter().map(value::to_bytes).collect(),
            redis::Value::Nil => Ok(Vec::new()),
            other => Ok(vec![value::to_bytes(other)?]),
        }
    }

    /// Add geospatial members with options (`GEOADD` with `NX`/`XX`/`CH`).
    /// Returns the number of added (or, with `changed`, changed) members.
    async fn geoadd_options<K: ToRedisArgs + Send, M: ToRedisArgs + Send + Sync>(
        &self,
        key: K,
        members_positions: &[(M, GeospatialData)],
        conditional_change: Option<ConditionalChange>,
        changed: bool,
    ) -> Result<i64> {
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
        value::to_i64(self.execute_command(cmd, None).await?)
    }

    /// Search a geospatial index from a member with a given shape (`GEOSEARCH
    /// ... FROMMEMBER ... BYRADIUS|BYBOX`). Returns matching member names.
    async fn geosearch_from_member<K: ToRedisArgs + Send, M: ToRedisArgs + Send>(
        &self,
        key: K,
        member: M,
        shape: GeoSearchShape,
        order: Option<OrderBy>,
        count: Option<i64>,
        any: bool,
    ) -> Result<Vec<Bytes>> {
        let mut cmd = Cmd::new();
        cmd.arg("GEOSEARCH").arg(key).arg("FROMMEMBER").arg(member);
        shape.add_to(&mut cmd);
        add_search_tail(&mut cmd, order, count, any);
        collect_bytes(self.execute_command(cmd, None).await?)
    }

    /// Search a geospatial index from a coordinate with a given shape
    /// (`GEOSEARCH ... FROMLONLAT ... BYRADIUS|BYBOX`).
    async fn geosearch_from_coord<K: ToRedisArgs + Send>(
        &self,
        key: K,
        coord: GeospatialData,
        shape: GeoSearchShape,
        order: Option<OrderBy>,
        count: Option<i64>,
        any: bool,
    ) -> Result<Vec<Bytes>> {
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
        D: ToRedisArgs + Send,
        S: ToRedisArgs + Send,
        M: ToRedisArgs + Send,
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
    ) -> Result<i64> {
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
        value::to_i64(self.execute_command(cmd, None).await?)
    }

    /// Search from a coordinate and store the results into `destination`
    /// (`GEOSEARCHSTORE ... FROMLONLAT`).
    async fn geosearchstore_from_coord<D: ToRedisArgs + Send, S: ToRedisArgs + Send>(
        &self,
        destination: D,
        source: S,
        coord: GeospatialData,
        shape: GeoSearchShape,
        order: Option<OrderBy>,
        count: Option<i64>,
        any: bool,
        store_dist: bool,
    ) -> Result<i64> {
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
        value::to_i64(self.execute_command(cmd, None).await?)
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

fn collect_bytes(v: redis::Value) -> Result<Vec<Bytes>> {
    match v {
        redis::Value::Array(items) => items.into_iter().map(value::to_bytes).collect(),
        redis::Value::Nil => Ok(Vec::new()),
        other => Ok(vec![value::to_bytes(other)?]),
    }
}

impl<T: CommandExecutor + ?Sized> GeoCommands for T {}

#[cfg(test)]
mod tests {
    use super::*;

    fn args_of(cmd: &Cmd) -> Vec<String> {
        cmd.args_iter()
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
