/*******************************************************************************
 * Copyright (c) 2013, Salesforce.com, Inc.
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 *     Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *     Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *     Neither the name of Salesforce.com nor the names of its contributors may 
 *     be used to endorse or promote products derived from this software without 
 *     specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE 
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL 
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR 
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER 
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, 
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE 
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************/
package com.salesforce.phoenix.flume;

import static com.salesforce.phoenix.util.TestUtil.TEST_PROPERTIES;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.flume.Channel;
import org.apache.flume.Context;
import org.apache.flume.Event;
import org.apache.flume.EventDeliveryException;
import org.apache.flume.Transaction;
import org.apache.flume.channel.MemoryChannel;
import org.apache.flume.conf.Configurables;
import org.apache.flume.event.EventBuilder;
import org.apache.flume.lifecycle.LifecycleState;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.Test;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.salesforce.phoenix.end2end.BaseHBaseManagedTimeTest;
import com.salesforce.phoenix.flume.serializer.EventSerializers;
import com.salesforce.phoenix.flume.sink.PhoenixSink;
import com.salesforce.phoenix.util.TestUtil;

public class TestRegexEventSerializer extends BaseHBaseManagedTimeTest {

    private Context sinkContext;
    private PhoenixSink sink;
    
    @Test
    public void testKeyGenerator() throws EventDeliveryException, SQLException {
        
        final String fullTableName = "FLUME_TEST";
        initSinkContextWithDefaults(fullTableName);
        
        sink = new PhoenixSink();
        Configurables.configure(sink, sinkContext);
 
        assertEquals(LifecycleState.IDLE, sink.getLifecycleState());
      
        final Channel channel = this.initChannel();
        sink.setChannel(channel);
        
        sink.start();
        final String eventBody = "val1\tval2";
        final Event event = EventBuilder.withBody(Bytes.toBytes(eventBody));
        // put event in channel
        Transaction transaction = channel.getTransaction();
        transaction.begin();
        channel.put(event);
        transaction.commit();
        transaction.close();

        sink.process();
       
        int rowsInDb = countRows(fullTableName);
        assertEquals(1 , rowsInDb);
        
        sink.stop();
        assertEquals(LifecycleState.STOP, sink.getLifecycleState());
         
    }
    
    
    @Test
    public void testMismatchKeyGenerator() throws EventDeliveryException, SQLException {
        
        final String fullTableName = "FLUME_TEST";
        initSinkContextWithDefaults(fullTableName);
        setConfig(FlumeConstants.CONFIG_SERIALIZER_PREFIX + FlumeConstants.CONFIG_ROWKEY_TYPE_GENERATOR,DefaultKeyGenerator.UUID.name());
     
        sink = new PhoenixSink();
        Configurables.configure(sink, sinkContext);
        assertEquals(LifecycleState.IDLE, sink.getLifecycleState());
       
      
        final Channel channel = this.initChannel();
        sink.setChannel(channel);
        
        sink.start();
        final String eventBody = "val1\tval2";
        final Event event = EventBuilder.withBody(Bytes.toBytes(eventBody));
        // put event in channel
        Transaction transaction = channel.getTransaction();
        transaction.begin();
        channel.put(event);
        transaction.commit();
        transaction.close();

        try {
            sink.process();
            fail();
        }catch(Exception ex){
            assertTrue(ex.getCause().getMessage().contains("java.text.ParseException: Unparseable date:"));
        }
     }
    
    @Test
    public void testMissingColumnsInEvent() throws EventDeliveryException, SQLException {
        
        final String fullTableName = "FLUME_TEST";
        initSinkContextWithDefaults(fullTableName);
      
        sink = new PhoenixSink();
        Configurables.configure(sink, sinkContext);
        assertEquals(LifecycleState.IDLE, sink.getLifecycleState());
      
        final Channel channel = this.initChannel();
        sink.setChannel(channel);
        
        sink.start();
        final String eventBody = "val1";
        final Event event = EventBuilder.withBody(Bytes.toBytes(eventBody));
        // put event in channel
        Transaction transaction = channel.getTransaction();
        transaction.begin();
        channel.put(event);
        transaction.commit();
        transaction.close();

        sink.process();
        
        int rowsInDb = countRows(fullTableName);
        assertEquals(0 , rowsInDb);
           
        sink.stop();
        assertEquals(LifecycleState.STOP, sink.getLifecycleState());
        
    }
    
    @Test
    public void testBatchEvents() throws EventDeliveryException, SQLException {
        
        final String fullTableName = "FLUME_TEST";
        initSinkContextWithDefaults(fullTableName);
      
        sink = new PhoenixSink();
        Configurables.configure(sink, sinkContext);
        assertEquals(LifecycleState.IDLE, sink.getLifecycleState());
      
        final Channel channel = this.initChannel();
        sink.setChannel(channel);
        
        sink.start();
        int numEvents = 150;
        String col1 = "val1";
        String col2 = "val2";
        String eventBody = null;
        List<Event> eventList = Lists.newArrayListWithCapacity(numEvents);
        for(int i = 0 ; i < eventList.size() ; i++) {
            eventBody = (col1 + i) + "\t" + (col2 + i);
            Event event = EventBuilder.withBody(Bytes.toBytes(eventBody));
            eventList.add(event);
        }
       
        // put event in channel
        Transaction transaction = channel.getTransaction();
        transaction.begin();
        for(Event event : eventList) {
            channel.put(event);
        }
        transaction.commit();
        transaction.close();

        sink.process();
        
        int rowsInDb = countRows(fullTableName);
        assertEquals(eventList.size(), rowsInDb);
        
        sink.stop();
        assertEquals(LifecycleState.STOP, sink.getLifecycleState());
        
    }
    
    @Test
    public void testApacheLogRegex() throws Exception {
        
        sinkContext = new Context ();
        final String fullTableName = "s1.apachelogs";
        final String logRegex = "([^ ]*) ([^ ]*) ([^ ]*) (-|\\[[^\\]]*\\]) \"([^ ]+) ([^ ]+)" +
                                " ([^\"]+)\" (-|[0-9]*) (-|[0-9]*)(?: ([^ \"]*|\"[^\"]*\")" +
                                " ([^ \"]*|\"[^\"]*\"))?";
        
        final String columns = "host,identity,user,time,method,request,protocol,status,size,referer,agent";
        
        String ddl = "CREATE TABLE " + fullTableName +
                "  (user VARCHAR not null, time varchar not null, host varchar , identity varchar, method varchar, request varchar , protocol varchar," +
                "  status integer , size integer , referer varchar , agent varchar CONSTRAINT pk PRIMARY KEY (user,time))\n";
       
        sinkContext.put(FlumeConstants.CONFIG_TABLE, fullTableName);
        sinkContext.put(FlumeConstants.CONFIG_JDBC_URL, TestUtil.PHOENIX_JDBC_URL);
        sinkContext.put(FlumeConstants.CONFIG_SERIALIZER,EventSerializers.REGEX.name());
        sinkContext.put(FlumeConstants.CONFIG_TABLE_DDL, ddl);
        sinkContext.put(FlumeConstants.CONFIG_SERIALIZER_PREFIX + FlumeConstants.CONFIG_REGULAR_EXPRESSION,logRegex);
        sinkContext.put(FlumeConstants.CONFIG_SERIALIZER_PREFIX + FlumeConstants.CONFIG_COLUMN_NAMES,columns);
       
        String message1 = "33.22.11.00 - user1 [12/Dec/2013:07:01:19 +0000] " +
                "\"GET /wp-admin/css/install.css HTTP/1.0\" 200 813 " + 
                "\"http://www.google.com\" \"Mozilla/5.0 (comp" +
                "atible; Yahoo! Slurp; http://help.yahoo.com/help/us/ysearch/slurp)\"";
        
        String message2 = "192.168.20.1 - user2 [13/Dec/2013:06:05:19 +0000] " +
                "\"GET /wp-admin/css/install.css HTTP/1.0\" 400 363 " + 
                "\"http://www.salesforce.com/in/?ir=1\" \"Mozilla/5.0 (comp" +
                "atible;)\"";
        
        
        sink = new PhoenixSink();
        Configurables.configure(sink, sinkContext);
        assertEquals(LifecycleState.IDLE, sink.getLifecycleState());
      
        final Channel channel = this.initChannel();
        sink.setChannel(channel);
        
        sink.start();
        
        final Event event1 = EventBuilder.withBody(Bytes.toBytes(message1));
        final Event event2 = EventBuilder.withBody(Bytes.toBytes(message2));
        
        final Transaction transaction = channel.getTransaction();
        transaction.begin();
        channel.put(event1);
        channel.put(event2);
        transaction.commit();
        transaction.close();

        sink.process();
   
        final String query = " SELECT * FROM \n " + fullTableName;
        Properties props = new Properties(TEST_PROPERTIES);
        final ResultSet rs ;
        final Connection conn = DriverManager.getConnection(getUrl(), props);
        try{
            rs = conn.createStatement().executeQuery(query);
            assertTrue(rs.next());
            assertEquals("33.22.11.00",rs.getString("host"));
            assertEquals("user1",rs.getString("user"));
            
            assertTrue(rs.next());
            assertEquals("192.168.20.1",rs.getString("host"));
            assertEquals("user2",rs.getString("user")); 
        }finally {
            if(conn != null) {
                conn.close();
            }
        }
        sink.stop();
        assertEquals(LifecycleState.STOP, sink.getLifecycleState());
        
    }
              
    @Test
    public void testEventsWithHeaders() throws Exception {
        
        sinkContext = new Context ();
        final String fullTableName = "FLUME_TEST";
        final String ddl = "CREATE TABLE " + fullTableName +
                "  (rowkey VARCHAR not null, col1 varchar , cf1.col2 varchar , host varchar , source varchar \n" +
                "  CONSTRAINT pk PRIMARY KEY (rowkey))\n";
       
        sinkContext.put(FlumeConstants.CONFIG_TABLE, fullTableName);
        sinkContext.put(FlumeConstants.CONFIG_JDBC_URL, TestUtil.PHOENIX_JDBC_URL);
        sinkContext.put(FlumeConstants.CONFIG_SERIALIZER,EventSerializers.REGEX.name());
        sinkContext.put(FlumeConstants.CONFIG_TABLE_DDL, ddl);
        sinkContext.put(FlumeConstants.CONFIG_SERIALIZER_PREFIX + FlumeConstants.CONFIG_REGULAR_EXPRESSION,"^([^\t]+)\t([^\t]+)$");
        sinkContext.put(FlumeConstants.CONFIG_SERIALIZER_PREFIX + FlumeConstants.CONFIG_COLUMN_NAMES,"col1,cf1.col2");
        sinkContext.put(FlumeConstants.CONFIG_SERIALIZER_PREFIX + FlumeConstants.CONFIG_HEADER_NAMES,"host,source");
        sinkContext.put(FlumeConstants.CONFIG_SERIALIZER_PREFIX + FlumeConstants.CONFIG_ROWKEY_TYPE_GENERATOR,DefaultKeyGenerator.UUID.name());       
        
        sink = new PhoenixSink();
        Configurables.configure(sink, sinkContext);
        assertEquals(LifecycleState.IDLE, sink.getLifecycleState());
      
        final Channel channel = this.initChannel();
        sink.setChannel(channel);
        
        sink.start();
        
        int numEvents = 10;
        String col1 = "val1";
        String col2 = "val2";
        String hostHeader = "host1";
        String sourceHeader = "source1";
        String eventBody = null;
        List<Event> eventList = Lists.newArrayListWithCapacity(numEvents);
        for(int i = 0 ; i < numEvents ; i++) {
            eventBody = (col1 + i) + "\t" + (col2 + i);
            Map<String, String> headerMap = Maps.newHashMapWithExpectedSize(2);
            headerMap.put("host",hostHeader);
            headerMap.put("source",sourceHeader);
            Event event = EventBuilder.withBody(Bytes.toBytes(eventBody),headerMap);
            eventList.add(event);
        }
       
        // put event in channel
        Transaction transaction = channel.getTransaction();
        transaction.begin();
        for(Event event : eventList) {
            channel.put(event);
        }
        transaction.commit();
        transaction.close();
        
        sink.process();
   
        final String query = " SELECT * FROM \n " + fullTableName;
        Properties props = new Properties(TEST_PROPERTIES);
        final ResultSet rs ;
        final Connection conn = DriverManager.getConnection(getUrl(), props);
        try{
            rs = conn.createStatement().executeQuery(query);
            assertTrue(rs.next());
            assertEquals("host1",rs.getString("host"));
            assertEquals("source1",rs.getString("source"));
            
            assertTrue(rs.next());
            assertEquals("host1",rs.getString("host"));
            assertEquals("source1",rs.getString("source")); 
        }finally {
            if(conn != null) {
                conn.close();
            }
        }
        sink.stop();
        assertEquals(LifecycleState.STOP, sink.getLifecycleState());
        
    }
    
    private Channel initChannel() {
        //Channel configuration
        Context channelContext = new Context();
        channelContext.put("capacity", "10000");
        channelContext.put("transactionCapacity", "200");

        Channel channel = new MemoryChannel();
        channel.setName("memorychannel");
        Configurables.configure(channel, channelContext);
        return channel;
    }
    
    private void initSinkContextWithDefaults(final String fullTableName) {
        Preconditions.checkNotNull(fullTableName);
        sinkContext = new Context ();
        String ddl = "CREATE TABLE " + fullTableName +
                "  (flume_time timestamp not null, col1 varchar , col2 varchar" +
                "  CONSTRAINT pk PRIMARY KEY (flume_time))\n";
       
        sinkContext.put(FlumeConstants.CONFIG_TABLE, fullTableName);
        sinkContext.put(FlumeConstants.CONFIG_JDBC_URL, TestUtil.PHOENIX_JDBC_URL);
        sinkContext.put(FlumeConstants.CONFIG_SERIALIZER,EventSerializers.REGEX.name());
        sinkContext.put(FlumeConstants.CONFIG_TABLE_DDL, ddl);
        sinkContext.put(FlumeConstants.CONFIG_SERIALIZER_PREFIX + FlumeConstants.CONFIG_REGULAR_EXPRESSION,"^([^\t]+)\t([^\t]+)$");
        sinkContext.put(FlumeConstants.CONFIG_SERIALIZER_PREFIX + FlumeConstants.CONFIG_COLUMN_NAMES,"col1,col2");
        sinkContext.put(FlumeConstants.CONFIG_SERIALIZER_PREFIX + FlumeConstants.CONFIG_ROWKEY_TYPE_GENERATOR,DefaultKeyGenerator.TIMESTAMP.name());
        
      }
    
    private void setConfig(final String configName , final String configValue) {
        Preconditions.checkNotNull(sinkContext);
        Preconditions.checkNotNull(configName);
        Preconditions.checkNotNull(configValue);
        sinkContext.put(configName, configValue);
    }
    
    private int countRows(final String fullTableName) throws SQLException {
        Preconditions.checkNotNull(fullTableName);
        Properties props = new Properties(TEST_PROPERTIES);
        final Connection conn = DriverManager.getConnection(getUrl(), props);
        ResultSet rs = null ;
        try{
            rs  = conn.createStatement().executeQuery("select count(*) from "+fullTableName);
            int rowsCount = 0;
            while(rs.next()) {
                rowsCount = rs.getInt(1);
            }
            return rowsCount;
            
        } finally {
            if(rs != null) {
                rs.close();
            }
            if(conn != null) {
                conn.close();
            }
        }
        
       
    }

}