// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Per-command geospatial integration tests (RESP2 + RESP3).

mod common;

use glide::{
    AsyncTypedCommands, ExistenceCheck, GeoCommands, GeoCoord, GeoSearchOptions, GeoSearchShape,
    GeoSearchStoreOptions, GeoUnit, GlideError, OrderBy,
};

matrix_test!(geo_add, c, {
    let k = common::key("geo");
    let added: usize = c
        .geo_add(&k, &[(palermo(), "Palermo"), (catania(), "Catania")])
        .await
        .unwrap();
    assert_eq!(added, 2);

    // Re-adding the same members returns 0 new.
    assert_eq!(c.geo_add(&k, (palermo(), "Palermo")).await.unwrap(), 0);
});

matrix_test!(geoadd_options, c, {
    let k = common::key("geoadd_options");
    let moved = GeoCoord::lon_lat(13.5, 38.5);

    // XX on a missing member adds nothing.
    assert_eq!(
        c.geoadd_options(
            &k,
            &[(palermo(), "Palermo")],
            Some(ExistenceCheck::XX),
            false,
        )
        .await
        .unwrap(),
        0
    );

    // NX adds a new member.
    let n = c
        .geoadd_options(
            &k,
            &[(palermo(), "Palermo")],
            Some(ExistenceCheck::NX),
            false,
        )
        .await
        .unwrap();
    assert_eq!(n, 1);

    // NX does not update an existing member, even with CH.
    assert_eq!(
        c.geoadd_options(&k, &[(moved, "Palermo")], Some(ExistenceCheck::NX), true)
            .await
            .unwrap(),
        0
    );

    // XX updates an existing member; CH counts it as changed.
    let n = c
        .geoadd_options(&k, &[(moved, "Palermo")], Some(ExistenceCheck::XX), true)
        .await
        .unwrap();
    assert_eq!(n, 1);
});

matrix_test!(geo_dist, c, {
    let k = common::key("geo_dist");
    c.geo_add(&k, (palermo(), "Palermo")).await.unwrap();
    c.geo_add(&k, (catania(), "Catania")).await.unwrap();

    // Known distance ~166 km (~103 mi).
    let km: Option<f64> = c
        .geo_dist(&k, "Palermo", "Catania", GeoUnit::Kilometers)
        .await
        .unwrap();
    assert!((160.0..170.0).contains(&km.unwrap()));
    let mi: Option<f64> = c
        .geo_dist(&k, "Palermo", "Catania", GeoUnit::Miles)
        .await
        .unwrap();
    assert!((100.0..106.0).contains(&mi.unwrap()));

    // A missing member has no distance.
    let missing: Option<f64> = c
        .geo_dist(&k, "Palermo", "Nowhere", GeoUnit::Meters)
        .await
        .unwrap();
    assert_eq!(missing, None);
});

matrix_test!(geo_hash, c, {
    let k = common::key("geo");
    c.geo_add(&k, (palermo(), "Palermo")).await.unwrap();

    let hashes: Vec<String> = c.geo_hash(&k, "Palermo").await.unwrap();
    assert_eq!(hashes, vec!["sqc8b49rny0".to_string()]);
});

// The Valkey server returns `nil` when a requested member does not exist.
// Typed `geo_hash` raises an error if this happens. Callers need
// to use the untyped version if they want to handle this case.
// This matches redis-rs behaviour.

matrix_test!(geo_hash_typed_with_nil, c, {
    let k = common::key("geo");
    c.geo_add(&k, (palermo(), "Palermo")).await.unwrap();
    assert!(c.geo_hash(&k, &["Palermo", "Missing"]).await.is_err());
});

matrix_test!(geo_hash_untyped_with_nil, c, {
    let k = common::key("geo");
    c.geo_add(&k, (palermo(), "Palermo")).await.unwrap();

    let hashes: Vec<Option<String>> =
        glide::AsyncCommands::geo_hash(&c, &k, &["Palermo", "Missing"])
            .await
            .unwrap();
    assert_eq!(hashes, vec![Some("sqc8b49rny0".to_string()), None]);
});

matrix_test!(geo_pos, c, {
    let k = common::key("geo");
    c.geo_add(&k, (palermo(), "Palermo")).await.unwrap();

    let positions: Vec<Option<GeoCoord<f64>>> =
        c.geo_pos(&k, &["Palermo", "Missing"]).await.unwrap();
    assert!((positions[0].unwrap().longitude - 13.361389).abs() < 0.001);
    assert!((positions[0].unwrap().latitude - 38.115556).abs() < 0.001);
    assert_eq!(positions[1], None);
});

matrix_test!(geosearch_by_radius, c, {
    let k = common::key("geo");
    c.geo_add(&k, (palermo(), "Palermo")).await.unwrap();
    c.geo_add(&k, (catania(), "Catania")).await.unwrap();

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

matrix_test!(geosearch, c, {
    let k = common::key("geosearch");
    c.geo_add(&k, &[(palermo(), "Palermo"), (catania(), "Catania")])
        .await
        .unwrap();
    let shape = GeoSearchShape::ByRadius {
        radius: 200.0,
        unit: GeoUnit::Kilometers,
    };

    // Without `WITH*` flags, only member names are returned.
    let options = GeoSearchOptions {
        order: Some(OrderBy::Asc),
        ..Default::default()
    };
    let found = c
        .geosearch_from_member(&k, "Palermo", shape, options)
        .await
        .unwrap();
    let names: Vec<_> = found.iter().map(|r| r.member.as_ref()).collect();
    assert_eq!(names, vec![b"Palermo".as_slice(), b"Catania".as_slice()]);
    assert!(
        found
            .iter()
            .all(|r| r.position.is_none() && r.distance.is_none() && r.hash.is_none())
    );

    // With every `WITH*` flag.
    let options = GeoSearchOptions {
        order: Some(OrderBy::Asc),
        with_position: true,
        with_distance: true,
        with_hash: true,
        ..Default::default()
    };
    let found = c
        .geosearch_from_coord(&k, palermo(), shape, options)
        .await
        .unwrap();
    assert_eq!(found.len(), 2);
    assert_eq!(found[0].member.as_ref(), b"Palermo");
    assert!(found[0].distance.unwrap() < 0.001);
    assert_eq!(found[0].hash, Some(3479099956230698));
    let position = found[0].position.unwrap();
    assert!((position.longitude - palermo().longitude).abs() < 0.001);
    assert!((position.latitude - palermo().latitude).abs() < 0.001);
    assert_eq!(found[1].member.as_ref(), b"Catania");
    assert!((found[1].distance.unwrap() - 166.274).abs() < 0.01);

    // With a single `WITH*` flag.
    let options = GeoSearchOptions {
        count: Some(1),
        with_distance: true,
        ..Default::default()
    };
    let found = c
        .geosearch_from_member(&k, "Catania", shape, options)
        .await
        .unwrap();
    assert_eq!(found.len(), 1);
    assert_eq!(found[0].member.as_ref(), b"Catania");
    assert_eq!(found[0].distance, Some(0.0));
    assert_eq!((found[0].position, found[0].hash), (None, None));

    // The server rejects `any` without `count`.
    let options = GeoSearchOptions {
        any: true,
        ..Default::default()
    };
    let err = c
        .geosearch_from_member(&k, "Palermo", shape, options)
        .await
        .unwrap_err();
    assert!(matches!(err, GlideError::Request(_)));
    assert!(err.message().contains("ANY argument requires COUNT"));
});

matrix_test!(geosearchstore, c, {
    let src = common::tkey("geosearchstore", "src");
    let dst = common::tkey("geosearchstore", "dst");
    c.geo_add(&src, &[(palermo(), "Palermo"), (catania(), "Catania")])
        .await
        .unwrap();
    let shape = GeoSearchShape::ByRadius {
        radius: 200.0,
        unit: GeoUnit::Kilometers,
    };

    let stored = c
        .geosearchstore_from_member(&dst, &src, "Palermo", shape, Default::default())
        .await
        .unwrap();
    assert_eq!(stored, 2);

    let options = GeoSearchStoreOptions {
        count: Some(1),
        store_dist: true,
        ..Default::default()
    };
    let stored = c
        .geosearchstore_from_coord(&dst, &src, catania(), shape, options)
        .await
        .unwrap();
    assert_eq!(stored, 1);
    let score: Option<f64> = c.zscore(&dst, "Catania").await.unwrap();
    assert!(score.unwrap() < 0.001);
});

matrix_test!(geo_wrong_type_errors, c, {
    let k = common::key("wt");
    c.set(&k, "notgeo").await.unwrap();
    assert_request_error!(c.geo_add(&k, (palermo(), "X")).await);
});

/// Returns the coordinates for Palermo.
fn palermo() -> GeoCoord<f64> {
    GeoCoord::lon_lat(13.361389, 38.115556)
}

/// Returns the coordinates for Catania.
fn catania() -> GeoCoord<f64> {
    GeoCoord::lon_lat(15.087269, 37.502669)
}
