// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Mock-executor unit tests for the geospatial command family.
use super::Mock;
use glide::commands::geo::{GeoCommands, GeoSearchShape, GeoUnit, GeospatialData};
use glide::commands::options::{ConditionalChange, OrderBy};
use redis::Value;

fn coord(lon: f64, lat: f64) -> GeospatialData {
    GeospatialData {
        longitude: lon,
        latitude: lat,
    }
}

#[tokio::test]
async fn geoadd_encoding() {
    let m = Mock::int(1);
    let n = m
        .geoadd("Sicily", &[("Palermo", coord(13.5, 38.5))])
        .await
        .unwrap();
    m.assert_args(&["GEOADD", "Sicily", "13.5", "38.5", "Palermo"]);
    assert_eq!(n, 1);
}

#[tokio::test]
async fn geoadd_options_encoding() {
    let m = Mock::int(1);
    m.geoadd_options(
        "Sicily",
        &[("Palermo", coord(13.5, 38.5))],
        Some(ConditionalChange::OnlyIfDoesNotExist),
        true,
    )
    .await
    .unwrap();
    m.assert_args(&["GEOADD", "Sicily", "NX", "CH", "13.5", "38.5", "Palermo"]);
}

#[tokio::test]
async fn geodist_encoding() {
    let m = Mock::bulk("166.27");
    let d = m
        .geodist("Sicily", "Palermo", "Catania", Some(GeoUnit::Kilometers))
        .await
        .unwrap();
    m.assert_args(&["GEODIST", "Sicily", "Palermo", "Catania", "km"]);
    assert_eq!(d, Some(166.27));
}

#[tokio::test]
async fn geohash_encoding() {
    let m = Mock::array(vec![Value::BulkString(b"sqc8b49rny0".to_vec()), Value::Nil]);
    let v = m
        .geohash("Sicily", &["Palermo", "NonExisting"])
        .await
        .unwrap();
    m.assert_args(&["GEOHASH", "Sicily", "Palermo", "NonExisting"]);
    assert_eq!(v.len(), 2);
    assert!(v[0].is_some());
    assert!(v[1].is_none());
}

#[tokio::test]
async fn geopos_encoding() {
    let m = Mock::array(vec![Value::Array(vec![
        Value::BulkString(b"13.5".to_vec()),
        Value::BulkString(b"38.5".to_vec()),
    ])]);
    let v = m.geopos("Sicily", &["Palermo"]).await.unwrap();
    m.assert_args(&["GEOPOS", "Sicily", "Palermo"]);
    assert_eq!(v, vec![Some((13.5, 38.5))]);
}

#[tokio::test]
async fn geosearch_by_radius_from_member() {
    let m = Mock::array(vec![Value::BulkString(b"Palermo".to_vec())]);
    m.geosearch_by_radius_from_member("Sicily", "Palermo", 5.5, GeoUnit::Kilometers)
        .await
        .unwrap();
    m.assert_args(&[
        "GEOSEARCH",
        "Sicily",
        "FROMMEMBER",
        "Palermo",
        "BYRADIUS",
        "5.5",
        "km",
    ]);
}

#[tokio::test]
async fn geosearch_from_member_with_tail() {
    let m = Mock::array(vec![Value::BulkString(b"Palermo".to_vec())]);
    m.geosearch_from_member(
        "Sicily",
        "Palermo",
        GeoSearchShape::ByRadius {
            radius: 5.5,
            unit: GeoUnit::Kilometers,
        },
        Some(OrderBy::Asc),
        Some(10),
        true,
    )
    .await
    .unwrap();
    m.assert_args(&[
        "GEOSEARCH",
        "Sicily",
        "FROMMEMBER",
        "Palermo",
        "BYRADIUS",
        "5.5",
        "km",
        "ASC",
        "COUNT",
        "10",
        "ANY",
    ]);
}

#[tokio::test]
async fn geosearch_from_coord_bybox() {
    let m = Mock::array(vec![Value::BulkString(b"Palermo".to_vec())]);
    m.geosearch_from_coord(
        "Sicily",
        coord(15.5, 37.5),
        GeoSearchShape::ByBox {
            width: 2.5,
            height: 3.5,
            unit: GeoUnit::Meters,
        },
        None,
        None,
        false,
    )
    .await
    .unwrap();
    m.assert_args(&[
        "GEOSEARCH",
        "Sicily",
        "FROMLONLAT",
        "15.5",
        "37.5",
        "BYBOX",
        "2.5",
        "3.5",
        "m",
    ]);
}

#[tokio::test]
async fn geosearchstore_from_member() {
    let m = Mock::int(2);
    m.geosearchstore_from_member(
        "dest",
        "Sicily",
        "Palermo",
        GeoSearchShape::ByRadius {
            radius: 5.5,
            unit: GeoUnit::Kilometers,
        },
        None,
        None,
        false,
        true,
    )
    .await
    .unwrap();
    m.assert_args(&[
        "GEOSEARCHSTORE",
        "dest",
        "Sicily",
        "FROMMEMBER",
        "Palermo",
        "BYRADIUS",
        "5.5",
        "km",
        "STOREDIST",
    ]);
}

#[tokio::test]
async fn geosearchstore_from_coord() {
    let m = Mock::int(2);
    m.geosearchstore_from_coord(
        "dest",
        "Sicily",
        coord(15.5, 37.5),
        GeoSearchShape::ByRadius {
            radius: 5.5,
            unit: GeoUnit::Kilometers,
        },
        None,
        Some(5),
        false,
        false,
    )
    .await
    .unwrap();
    m.assert_args(&[
        "GEOSEARCHSTORE",
        "dest",
        "Sicily",
        "FROMLONLAT",
        "15.5",
        "37.5",
        "BYRADIUS",
        "5.5",
        "km",
        "COUNT",
        "5",
    ]);
}
