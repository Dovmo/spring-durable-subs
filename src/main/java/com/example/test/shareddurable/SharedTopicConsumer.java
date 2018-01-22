package com.example.test.shareddurable;

import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

import javax.jms.ConnectionFactory;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.config.JmsListenerContainerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.listener.DefaultMessageListenerContainer;

@Configuration
@EnableJms
public class SharedTopicConsumer {
	
	private static final String CONNECTION_URL = "tcp://localhost:61616";
	private static final String TOPIC_NAME = "exampleTopic";
	
	@Bean(destroyMethod = "stop")
	public ActiveMQServer broker() throws URISyntaxException, Exception {
		ActiveMQServer server = ActiveMQServers.newActiveMQServer(new ConfigurationImpl()
                .setPersistenceEnabled(false)
                .setJournalDirectory("target/data/journal")
                .setSecurityEnabled(false)
                .addAcceptorConfiguration("tcp", CONNECTION_URL)
                .setJMXManagementEnabled(true)
                .setManagementAddress(new SimpleString("0.0.0.0"))
        );
		server.start();
		return server;
	}
	
	@Bean
	public ActiveMQConnectionFactory connectionFactory() {
		ActiveMQConnectionFactory cf = 
				new ActiveMQConnectionFactory(CONNECTION_URL);
		return cf;
	}
	
	@Bean
	public JmsListenerContainerFactory<DefaultMessageListenerContainer> jmsListenerContainerFactory(
			ConnectionFactory connectionFactory) {
		DefaultJmsListenerContainerFactory dmlc = new DefaultJmsListenerContainerFactory();
		dmlc.setConnectionFactory(connectionFactory);
		dmlc.setConcurrency("2-2");
		dmlc.setSubscriptionShared(true);
		return dmlc;
	}
	
	@Bean
	public JmsTemplate jmsTemplate(ConnectionFactory connectionFactory) {
		JmsTemplate jt = new JmsTemplate();
		jt.setConnectionFactory(connectionFactory);
		return jt;
	}
	
	@JmsListener(destination = TOPIC_NAME, subscription = "sc1")
	public void destinationListener(String testMessage) {
		System.out.println(testMessage);
	}
	
	public static void main(String [] args) throws Exception {
		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
		ctx.register(SharedTopicConsumer.class);
		ctx.refresh();
		
		// Send a message
		ctx.getBean(JmsTemplate.class).convertAndSend(TOPIC_NAME, "This is a string");
		
		// Wait more than enough time for the listener to consume the message
		TimeUnit.SECONDS.sleep(10);
		
		ctx.close();
	}

}
