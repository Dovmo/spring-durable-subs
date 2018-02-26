package com.example.test.shareddurable;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;

import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.config.JmsListenerContainerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessagePostProcessor;
import org.springframework.jms.listener.DefaultMessageListenerContainer;

@Configuration
@EnableJms
public class SharedTopicConsumer {
	
	private static final String CONNECTION_URL = "tcp://localhost:61616";
	private static final String TOPIC_NAME = "exampleTopic";
	
	private Logger logger = LoggerFactory.getLogger(SharedTopicConsumer.class);
	
	@Bean(destroyMethod = "stop")
	public ActiveMQServer testBroker() throws Exception {
		EmbeddedActiveMQ server = new EmbeddedActiveMQ();
		server.setConfigResourcePath("broker.xml");				
		server.start();
		return server.getActiveMQServer();
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
		
		// This sets the concurrency on the subscription, creating two message consumers
		dmlc.setConcurrency("5-10");
		dmlc.setSubscriptionShared(true);
		dmlc.setSubscriptionDurable(true);
		
		// Automatically set with the above #setSubscriptionShared, but doing this for good measure
		dmlc.setPubSubDomain(true);
		return dmlc;
	}
	
	@Bean
	public JmsTemplate jmsTemplate(ConnectionFactory connectionFactory) {
		JmsTemplate jt = new JmsTemplate();
		jt.setConnectionFactory(connectionFactory);
		return jt;
	}

	// Seems to be an error when attempting to create binding to the topic:
	//     "AMQ119018: Binding already exists LocalQueueBinding" 
	@JmsListener(destination = TOPIC_NAME, subscription = "sc1")
	public void destinationListener(String testMessage) {
		logger.info("Received test message: " + testMessage);
	}
	
	public static void main(String [] args) throws Exception {
		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
		ctx.register(SharedTopicConsumer.class);
		ctx.refresh();
		
		ActiveMQTopic topic = new ActiveMQTopic(TOPIC_NAME);
		
		// Send a message
		IntStream.range(0, 10).forEach(count -> {
			int randomId = ThreadLocalRandom.current().nextInt(0, 4);
			ctx.getBean(JmsTemplate.class).convertAndSend(topic, "This is a string #" + randomId,
					new MessagePostProcessor() {
						@Override
						public Message postProcessMessage(Message message) throws JMSException {
							message.setStringProperty("JMSXGroupID", "" + randomId);
							return message;
						}
					});
		});
		// Wait more than enough time for the listener to consume the message
		TimeUnit.SECONDS.sleep(10);
		
		ctx.close();
	}

}
