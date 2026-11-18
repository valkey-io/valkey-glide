// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Mock-executor unit tests for the geospatial command family.
use super::Mock;
use crate::ValkeyValue;
use crate::commands::geo::{
    GeoCommands, GeoCoord, GeoSearchOptions, GeoSearchResult, GeoSearchShape,
    GeoSearchStoreOptions, GeoUnit,
};
use crate::commands::options::{ExistenceCheck, OrderBy};

fn coord(lon: f64, lat: f64) -> GeoCoord<f64> {
    GeoCoord::lon_lat(lon, lat)
}

#[tokio::test]
async fn geoadd_options_encoding() {
    let m = Mock::int(1);
    m.geoadd_options(
        "Sicily",
        &[(coord(13.5, 38.5), "Palermo")],
        Some(ExistenceCheck::NX),
        true,
    )
    .await
    .unwrap();
    m.assert_args(&["GEOADD", "Sicily", "NX", "CH", "13.5", "38.5", "Palermo"]);
}

#[tokio::test]
async fn geosearch_by_radius_from_member() {
    let m = Mock::array(vec![ValkeyValue::BulkString(b"Palermo".to_vec().into())]);
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
    let m = Mock::array(vec![ValkeyValue::BulkString(b"Palermo".to_vec().into())]);
    m.geosearch_from_member(
        "Sicily",
        "Palermo",
        GeoSearchShape::ByRadius {
            radius: 5.5,
            unit: GeoUnit::Kilometers,
        },
        GeoSearchOptions {
            order: Some(OrderBy::Asc),
            count: Some(10),
            any: true,
            ..Default::default()
        },
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
async fn geosearch_from_member_with_extras() {
    let m = Mock::array(vec![ValkeyValue::Array(vec![
        ValkeyValue::BulkString(b"Palermo".to_vec().into()),
        ValkeyValue::Array(vec![
            ValkeyValue::Double(0.0),
            ValkeyValue::Int(3479099956230698),
            ValkeyValue::Array(vec![ValkeyValue::Double(13.5), ValkeyValue::Double(38.5)]),
        ]),
    ])]);
    let results = m
        .geosearch_from_member(
            "Sicily",
            "Palermo",
            GeoSearchShape::ByRadius {
                radius: 5.5,
                unit: GeoUnit::Kilometers,
            },
            GeoSearchOptions {
                with_position: true,
                with_distance: true,
                with_hash: true,
                ..Default::default()
            },
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
        "WITHCOORD",
        "WITHDIST",
        "WITHHASH",
    ]);
    assert_eq!(
        results,
        vec![GeoSearchResult {
            member: "Palermo".into(),
            position: Some(coord(13.5, 38.5)),
            distance: Some(0.0),
            hash: Some(3479099956230698),
        }]
    );
}

#[tokio::test]
async fn geosearch_from_coord_bybox() {
    let m = Mock::array(vec![ValkeyValue::BulkString(b"Palermo".to_vec().into())]);
    m.geosearch_from_coord(
        "Sicily",
        coord(15.5, 37.5),
        GeoSearchShape::ByBox {
            width: 2.5,
            height: 3.5,
            unit: GeoUnit::Meters,
        },
        GeoSearchOptions::default(),
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
        GeoSearchStoreOptions {
            store_dist: true,
            ..Default::default()
        },
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
        GeoSearchStoreOptions {
            count: Some(5),
            ..Default::default()
        },
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
