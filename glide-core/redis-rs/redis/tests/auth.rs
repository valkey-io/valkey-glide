mod support;

#[cfg(test)]
mod auth {
    use crate::support::*;
    use redis::{
        aio::MultiplexedConnection,
        cluster::ClusterClientBuilder,
        cluster_async::ClusterConnection,
        cluster_routing::{MultipleNodeRoutingInfo, ResponsePolicy, RoutingInfo},
        cmd, ConnectionInfo, GlideConnectionOptions, ProtocolVersion, RedisConnectionInfo,
        RedisResult, Value,
    };

    const ALL_SUCCESS_ROUTE: RoutingInfo = RoutingInfo::MultiNode((
        MultipleNodeRoutingInfo::AllNodes,
        Some(ResponsePolicy::AllSucceeded),
    ));

    const PASSWORD: &str = "password";
    const NEW_PASSWORD: &str = "new_password";

    enum ConnectionType {
        Cluster,
        Standalone,
    }

    enum Connection {
        Cluster(ClusterConnection),
        Standalone(MultiplexedConnection),
    }

    async fn create_connection(
        password: Option<String>,
        connection_type: ConnectionType,
        cluster_context: Option<&TestClusterContext>,
        standalone_context: Option<&TestContext>,
    ) -> RedisResult<Connection> {
        match connection_type {
            ConnectionType::Cluster => {
                let cluster_context =
                    cluster_context.expect("ClusterContext is required for Cluster connection");
                let builder = get_builder(cluster_context, password);
                let connection = builder.build()?.get_async_connection(None).await?;
                Ok(Connection::Cluster(connection))
            }
            ConnectionType::Standalone => {
                let standalone_context =
                    standalone_context.expect("TestContext is required for Standalone connection");
                let info = get_connection_info(standalone_context, password);
                let client = redis::Client::open(info)?;
                let connection = client
                    .get_multiplexed_tokio_connection(GlideConnectionOptions::default())
                    .await?;
                Ok(Connection::Standalone(connection))
            }
        }
    }

    fn get_connection_info(context: &TestContext, password: Option<String>) -> ConnectionInfo {
        let addr = context.server.connection_info().addr.clone();
        ConnectionInfo {
            addr,
            redis: RedisConnectionInfo {
                password,
                protocol: context.protocol,
                ..Default::default()
            },
        }
    }

    fn get_builder(context: &TestClusterContext, password: Option<String>) -> ClusterClientBuilder {
        let mut builder = ClusterClientBuilder::new(context.nodes.clone());
        if let Some(password) = password {
            builder = builder.password(password);
        }
        builder.use_protocol(context.protocol)
    }

    async fn set_password(password: &str, conn: &mut Connection) -> RedisResult<()> {
        let mut set_auth_cmd = cmd("config");
        set_auth_cmd.arg("set").arg("requirepass").arg(password);
        match conn {
            Connection::Cluster(cluster_conn) => cluster_conn
                .route_command(&set_auth_cmd, ALL_SUCCESS_ROUTE)
                .await
                .map(|_| ()),
            Connection::Standalone(standalone_conn) => set_auth_cmd
                .query_async::<_, ()>(standalone_conn)
                .await
                .map(|_| ()),
        }
    }

    async fn kill_non_management_connections(con: &mut Connection) {
        let mut kill_cmd = cmd("client");
        kill_cmd.arg("kill").arg("type").arg("normal");
        match con {
            Connection::Cluster(cluster_conn) => {
                cluster_conn
                    .route_command(&kill_cmd, ALL_SUCCESS_ROUTE)
                    .await
                    .unwrap();
            }
            Connection::Standalone(standalone_conn) => {
                kill_cmd.arg("skipme").arg("no");
                kill_cmd
                    .query_async::<_, ()>(standalone_conn)
                    .await
                    .unwrap();
            }
        }
    }

    #[tokio::test]
    #[serial_test::serial]
    async fn test_replace_password_cluster() {
        let cluster_context = TestClusterContext::new(3, 0);

        // Create a management connection to set the password
        let management_connection =
            match create_connection(None, ConnectionType::Cluster, Some(&cluster_context), None)
                .await
                .unwrap()
            {
                Connection::Cluster(conn) => conn,
                _ => panic!("Expected ClusterConnection"),
            };

        // Set the password using the unified function
        let mut management_conn = Connection::Cluster(management_connection.clone());
        set_password(PASSWORD, &mut management_conn).await.unwrap();

        // Test that we can't connect without password
        let connection_should_fail =
            create_connection(None, ConnectionType::Cluster, Some(&cluster_context), None).await;
        assert!(connection_should_fail.is_err());
        let err = connection_should_fail.err().unwrap();
        assert!(err.to_string().contains("Authentication required."));

        // Test that we can connect with password
        let mut connection_should_succeed = match create_connection(
            Some(PASSWORD.to_string()),
            ConnectionType::Cluster,
            Some(&cluster_context),
            None,
        )
        .await
        .unwrap()
        {
            Connection::Cluster(conn) => conn,
            _ => panic!("Expected ClusterConnection"),
        };

        let res: RedisResult<Value> = cmd("set")
            .arg("foo")
            .arg("bar")
            .query_async(&mut connection_should_succeed)
            .await;
        assert_eq!(res.unwrap(), Value::Okay);

        // Verify that we can retrieve the set value
        let res: RedisResult<Value> = cmd("get")
            .arg("foo")
            .query_async(&mut connection_should_succeed)
            .await;
        assert_eq!(res.unwrap(), Value::BulkString(b"bar".to_vec()));

        // Kill the connection to force reconnection
        kill_non_management_connections(&mut Connection::Cluster(management_connection.clone()))
            .await;

        // Attempt to get the value again to ensure reconnection works
        let should_be_ok: RedisResult<Value> = cmd("get")
            .arg("foo")
            .query_async(&mut connection_should_succeed)
            .await;
        assert_eq!(should_be_ok.unwrap(), Value::BulkString(b"bar".to_vec()));

        // Update the password in the connection
        connection_should_succeed
            .update_connection_password(Some(NEW_PASSWORD.to_string()))
            .await
            .unwrap();

        // Update the password on the server
        let mut management_conn = Connection::Cluster(management_connection.clone());
        set_password(NEW_PASSWORD, &mut management_conn)
            .await
            .unwrap();

        // Test that we can't connect with the old password
        let connection_should_fail = create_connection(
            Some(PASSWORD.to_string()),
            ConnectionType::Cluster,
            Some(&cluster_context),
            None,
        )
        .await;
        assert!(connection_should_fail.is_err());
        let err = connection_should_fail.err().unwrap();
        assert!(err
            .to_string()
            .contains("Password authentication failed- AuthenticationFailed"));

        // Kill the connection to force reconnection
        let mut management_conn = Connection::Cluster(management_connection);
        kill_non_management_connections(&mut management_conn).await;

        // Verify that the connection with new password still works
        let result_should_succeed: RedisResult<Value> = cmd("get")
            .arg("foo")
            .query_async(&mut connection_should_succeed)
            .await;
        assert!(result_should_succeed.is_ok());
        assert_eq!(
            result_should_succeed.unwrap(),
            Value::BulkString(b"bar".to_vec())
        );
    }

    #[tokio::test]
    #[serial_test::serial]
    async fn test_replace_password_standalone() {
        let mut standalone_context = TestContext::new();
        standalone_context.protocol = ProtocolVersion::RESP2;

        // Create a management connection to set the password
        let management_connection = match create_connection(
            None,
            ConnectionType::Standalone,
            None,
            Some(&standalone_context),
        )
        .await
        .unwrap()
        {
            Connection::Standalone(conn) => conn,
            _ => panic!("Expected Standalone connection"),
        };

        // Set the password using the unified function
        let mut management_conn = Connection::Standalone(management_connection.clone());
        set_password(PASSWORD, &mut management_conn).await.unwrap();

        // Test that we can't send commands with new connection without password
        let connection_should_fail = create_connection(
            None,
            ConnectionType::Standalone,
            None,
            Some(&standalone_context),
        )
        .await;
        let res_should_fail: RedisResult<Value> = match connection_should_fail.unwrap() {
            Connection::Cluster(mut conn) => cmd("get").arg("foo").query_async(&mut conn).await,
            Connection::Standalone(mut conn) => cmd("get").arg("foo").query_async(&mut conn).await,
        };
        assert!(res_should_fail.is_err());

        // Test that we can connect with password
        let mut connection_should_succeed = match create_connection(
            Some(PASSWORD.to_string()),
            ConnectionType::Standalone,
            None,
            Some(&standalone_context),
        )
        .await
        .unwrap()
        {
            Connection::Standalone(conn) => conn,
            _ => panic!("Expected Standalone connection"),
        };

        let res: RedisResult<Value> = cmd("set")
            .arg("foo")
            .arg("bar")
            .query_async(&mut connection_should_succeed)
            .await;
        assert_eq!(res.unwrap(), Value::Okay);

        // Update the password in the connection
        connection_should_succeed
            .update_connection_password(Some(NEW_PASSWORD.to_string()))
            .await
            .unwrap();

        // Update the password on the server
        let mut management_conn = Connection::Standalone(management_connection.clone());
        set_password(NEW_PASSWORD, &mut management_conn)
            .await
            .unwrap();

        // Reset the management connection
        kill_non_management_connections(&mut management_conn).await;

        // Test that we can't connect with the old password
        let connection_should_fail = create_connection(
            Some(PASSWORD.to_string()),
            ConnectionType::Standalone,
            None,
            Some(&standalone_context),
        )
        .await;
        assert!(connection_should_fail.is_err());
    }
}
