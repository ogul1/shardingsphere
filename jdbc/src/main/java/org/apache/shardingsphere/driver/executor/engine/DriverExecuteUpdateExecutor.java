/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.driver.executor.engine;

import lombok.RequiredArgsConstructor;
import org.apache.shardingsphere.driver.executor.callback.add.StatementAddCallback;
import org.apache.shardingsphere.driver.executor.callback.execute.StatementExecuteUpdateCallback;
import org.apache.shardingsphere.driver.executor.callback.replay.StatementReplayCallback;
import org.apache.shardingsphere.driver.executor.engine.pushdown.DriverPushDownExecuteUpdateExecutor;
import org.apache.shardingsphere.driver.jdbc.core.connection.ShardingSphereConnection;
import org.apache.shardingsphere.infra.connection.kernel.KernelProcessor;
import org.apache.shardingsphere.infra.executor.sql.context.ExecutionContext;
import org.apache.shardingsphere.infra.executor.sql.execute.engine.driver.jdbc.JDBCExecutionUnit;
import org.apache.shardingsphere.infra.executor.sql.execute.engine.driver.jdbc.JDBCExecutor;
import org.apache.shardingsphere.infra.executor.sql.execute.engine.raw.RawExecutor;
import org.apache.shardingsphere.infra.executor.sql.prepare.driver.DriverExecutionPrepareEngine;
import org.apache.shardingsphere.infra.metadata.ShardingSphereMetaData;
import org.apache.shardingsphere.infra.metadata.database.ShardingSphereDatabase;
import org.apache.shardingsphere.infra.session.query.QueryContext;
import org.apache.shardingsphere.traffic.executor.TrafficExecutor;
import org.apache.shardingsphere.traffic.rule.TrafficRule;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Driver execute update executor.
 */
@RequiredArgsConstructor
public final class DriverExecuteUpdateExecutor {
    
    private final ShardingSphereConnection connection;
    
    private final ShardingSphereMetaData metaData;
    
    private final DriverPushDownExecuteUpdateExecutor pushDownExecuteUpdateExecutor;
    
    private final TrafficExecutor trafficExecutor;
    
    public DriverExecuteUpdateExecutor(final ShardingSphereConnection connection, final ShardingSphereMetaData metaData, final JDBCExecutor jdbcExecutor, final RawExecutor rawExecutor,
                                       final TrafficExecutor trafficExecutor) {
        this.connection = connection;
        this.metaData = metaData;
        pushDownExecuteUpdateExecutor = new DriverPushDownExecuteUpdateExecutor(connection, metaData, jdbcExecutor, rawExecutor);
        this.trafficExecutor = trafficExecutor;
    }
    
    /**
     * Execute update.
     *
     * @param database database
     * @param queryContext query context
     * @param prepareEngine prepare engine
     * @param updateCallback statement execute update callback
     * @param replayCallback statement replay callback
     * @param addCallback statement add callback
     * @return updated row count
     * @throws SQLException SQL exception
     */
    @SuppressWarnings("rawtypes")
    public int executeUpdate(final ShardingSphereDatabase database, final QueryContext queryContext, final DriverExecutionPrepareEngine<JDBCExecutionUnit, Connection> prepareEngine,
                             final StatementExecuteUpdateCallback updateCallback, final StatementAddCallback addCallback, final StatementReplayCallback replayCallback) throws SQLException {
        Optional<String> trafficInstanceId = connection.getTrafficInstanceId(metaData.getGlobalRuleMetaData().getSingleRule(TrafficRule.class), queryContext);
        if (trafficInstanceId.isPresent()) {
            return trafficExecutor.execute(connection.getProcessId(), database.getName(), trafficInstanceId.get(), queryContext, prepareEngine, updateCallback::executeUpdate);
        }
        ExecutionContext executionContext = new KernelProcessor().generateExecutionContext(
                queryContext, database, metaData.getGlobalRuleMetaData(), metaData.getProps(), connection.getDatabaseConnectionManager().getConnectionContext());
        return pushDownExecuteUpdateExecutor.executeUpdate(database, executionContext, prepareEngine, updateCallback, addCallback, replayCallback);
    }
}
