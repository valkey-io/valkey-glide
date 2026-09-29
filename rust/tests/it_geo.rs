// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Per-command geospatial integration tests (RESP2 + RESP3).

mod common;

use glide::commands::geo::{GeoUnit, GeospatialData};
use glide::{AsyncCommands, GeoCommands};

fn palermo() -> GeospatialData {
    GeospatialData {
        longitude: 13.361389,
        latitude: 38.115556,
    }
}
fn catania() -> GeospatialData {
    GeospatialData {
        longitude: 15.087269,
        latitude: 37.502669,
    }
}

matrix_test!(geoadd, c, {
    let k = common::key("geo");
    let added = c
        .geoadd(&k, &[("Palermo", palermo()), ("Catania", catania())])
        .await
        .unwrap();
    assert_eq!(added, 2);
    // Re-adding the same members returns 0 new.
    assert_eq!(c.geoadd(&k, &[("Palermo", palermo())]).await.unwrap(), 0);
});

matrix_test!(geodist_km, c, {
    let k = common::key("geo");
    c.geoadd(&k, &[("Palermo", palermo()), ("Catania", catania())])
        .await
        .unwrap();
    let dist = c
        .geodist(&k, "Palermo", "Catania", Some(GeoUnit::Kilometers))
        .await
        .unwrap();
    let d = dist.unwrap();
    // Known distance ~166 km.
    assert!(d > 160.0 && d < 170.0);
});

matrix_test!(geodist_missing_member_none, c, {
    let k = common::key("geo");
    c.geoadd(&k, &[("Palermo", palermo())]).await.unwrap();
    assert_eq!(
        c.geodist(&k, "Palermo", "Nowhere", None).await.unwrap(),
        None
    );
});

matrix_test!(geohash, c, {
    let k = common::key("geo");
    c.geoadd(&k, &[("Palermo", palermo())]).await.unwrap();
    let hashes = c.geohash(&k, &["Palermo", "Missing"]).await.unwrap();
    assert!(hashes[0].is_some());
    assert!(hashes[1].is_none());
});

matrix_test!(geopos, c, {
    let k = common::key("geo");
    c.geoadd(&k, &[("Palermo", palermo())]).await.unwrap();
    let positions = c.geopos(&k, &["Palermo", "Missing"]).await.unwrap();
    let (lon, lat) = positions[0].unwrap();
    assert!((lon - 13.361389).abs() < 0.001);
    assert!((lat - 38.115556).abs() < 0.001);
    assert!(positions[1].is_none());
});

matrix_test!(geosearch_by_radius, c, {
    let k = common::key("geo");
    c.geoadd(&k, &[("Palermo", palermo()), ("Catania", catania())])
        .await
        .unwrap();
    let found = c
        .geosearch_by_radius_from_member(&k, "Palermo", 200.0, GeoUnit::Kilometers)
        .await
        .unwrap();
    assert_eq!(found.len(), 2);
    let narrow = c
        .geosearch_by_radius_from_member(&k, "Palermo", 1.0, GeoUnit::Kilometers)
        .await
        .unwrap();
    assert_eq!(narrow.len(), 1);
});

matrix_test!(geo_wrong_type_errors, c, {
    let k = common::key("wt");
    c.set::<_, _, ()>(&k, "notgeo").await.unwrap();
    assert_request_error!(c.geoadd(&k, &[("X", palermo())]).await);
});
