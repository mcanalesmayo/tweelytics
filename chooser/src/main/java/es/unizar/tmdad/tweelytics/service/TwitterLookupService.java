package es.unizar.tmdad.tweelytics.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.social.twitter.api.FilterStreamParameters;
import org.springframework.social.twitter.api.Stream;
import org.springframework.social.twitter.api.StreamListener;
import org.springframework.social.twitter.api.impl.TwitterTemplate;
import org.springframework.stereotype.Service;

import es.unizar.tmdad.tweelytics.config.MessageBrokerConfig;
import es.unizar.tmdad.tweelytics.domain.ComponentConfig;
import es.unizar.tmdad.tweelytics.domain.CustomTweet;
import es.unizar.tmdad.tweelytics.domain.QueriedTweet;
import es.unizar.tmdad.tweelytics.entities.AnalyzedTweetListenerContainer;
import es.unizar.tmdad.tweelytics.entities.AnalyzedTweetMessageHandler;
import es.unizar.tmdad.tweelytics.repository.ConfigsRepository;
import es.unizar.tmdad.tweelytics.service.SimpleStreamListener;

@Service
public class TwitterLookupService {
	
	@Autowired
	private ConfigsRepository configsRepository;
	
	@Autowired
	private TwitterTemplate twitterTemplate;
	
	@Autowired
	private RabbitTemplate rabbitTemplate;
	
	@Autowired
	private AnalyzedTweetListenerContainer analyzedTweetListenerContainer;
	
	@Autowired
	private AnalyzedTweetMessageHandler analyzedTweetMessageHandler;
	
	@Autowired
	private RabbitAdmin rabbitAdmin;
	
	@Value("${twitter.consumerKey}")
	private String consumerKey;
	
	@Value("${twitter.consumerSecret}")
	private String consumerSecret;
	
	@Value("${twitter.accessToken}")
	private String accessToken;
	
	@Value("${twitter.accessTokenSecret}")
	private String accessTokenSecret;
	
	@Value("${rabbitmq.toProcessorsTweetExchangeName}")
	private String toProcessorsTweetExchangeName;
	
	@Value("${rabbitmq.toChooserExchangeName}")
	private String toChooserExchangeName;
	
	@Value("${rabbitmq.toChooserQueueName}")
	private String toChooserQueueName;

	private ComponentConfig config;
	
	private static final int MAX_STREAMS = 10;
	
	// Queried streams
	// LinkedHashMap to guarantee FIFO replacement when max number of streams is reached (insertion-ordered)
	private LinkedHashMap<String, Stream> streams = new LinkedHashMap<String, Stream>(MAX_STREAMS){
		// Remove oldest entry when max number of streams is reached
		protected boolean removeEldestEntry(Map.Entry<String, Stream> eldest){
			return this.size() > MAX_STREAMS;
		}
	};
	
	public void search(String query) {
		fillComponentConfig();
		
		/* Experiment: measuring time to send a high number of tweets to the broker */
		/* 			   to know whether it is necessary more bandwith				*/
		/*int size = 1000;
		QueriedTweet[] list = new QueriedTweet[size];
		for(int i=0; i<size; i++){
			list[i] = new QueriedTweet();
			String s = "";
			// set 1024 bytes text to simulate sending a tweet with at least 1024 bytes
			for(int j=0; j<1024; j++) s += "a";
			//list[i].setText(s);
			list[i].setMyQuery("syria");
			list[i].setCustomTweet(new CustomTweet());
			list[i].getCustomTweet().setText(s);
		}
		
		System.out.println("Comienzo");
		long millis = System.currentTimeMillis();
		
		for(int i=0; i<size; i++){
			rabbitTemplate.convertAndSend(toProcessorsTweetExchangeName, "syria", list[i]);
		}
		
		millis = System.currentTimeMillis() - millis;
		System.out.println("Fin, tiempo: "+millis+" ms");
		*/
		FilterStreamParameters fsp = new FilterStreamParameters();
		fsp.track(query);
		
		List<StreamListener> l = new ArrayList<StreamListener>();
		l.add(new SimpleStreamListener(query, rabbitTemplate, toProcessorsTweetExchangeName));
		
		streams.putIfAbsent(query, twitterTemplate.streamingOperations()
				.filter(fsp, l));
		
		DirectExchange toChooserExchange = new DirectExchange(toChooserExchangeName, MessageBrokerConfig.DURABLE_QUEUES, MessageBrokerConfig.AUTODELETE_QUEUES);
		Queue queryQueue = new Queue(toChooserQueueName+query);
		rabbitAdmin.declareQueue(queryQueue);
		rabbitAdmin.declareBinding(BindingBuilder.bind(queryQueue).to(toChooserExchange).with(query));
		analyzedTweetListenerContainer.addQueueNames(toChooserQueueName+query);
    }
	
	public void setParam(String key, String value){
		fillComponentConfig();
		config.getParams().put(key, value);
		if (key.equals("highlightMode")) analyzedTweetMessageHandler.setHighlightMode(value);
		configsRepository.save(config);
	}
	
	private void fillComponentConfig(){
		if (config == null){
			config = configsRepository.findByComponent("chooser");
			if (config == null){
				config = new ComponentConfig();
			}
		}
		if (config.getParams() == null) config.setParams(new HashMap<String, Object>());
		if (config.getComponent() == null) config.setComponent("chooser");
		if (config.getParams().get("highlightMode") == null) config.setParam("highlightMode", "<strong>$1</strong>");
	}
}
