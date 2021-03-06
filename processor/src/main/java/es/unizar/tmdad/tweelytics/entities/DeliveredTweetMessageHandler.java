package es.unizar.tmdad.tweelytics.entities;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import es.unizar.tmdad.tweelytics.domain.AnalyzedTweet;
import es.unizar.tmdad.tweelytics.domain.QueriedTweet;

public class DeliveredTweetMessageHandler {
	
	private static final Logger logger = LoggerFactory.getLogger(DeliveredTweetMessageHandler.class);
	
	private RabbitTemplate rabbitTemplate;
	private String toChooserExchangeName;
	private Analyzer analyzer;
	
	public DeliveredTweetMessageHandler(RabbitTemplate rabbitTemplate, String toChooserExchangeName, Analyzer analyzer){
		this.rabbitTemplate = rabbitTemplate;
		this.toChooserExchangeName = toChooserExchangeName;
		this.analyzer = analyzer;
	}
	
	public void handleMessage(QueriedTweet queriedTweet) {
		logger.info("Processor got tweet for query: "+queriedTweet.getMyQuery());
		
		/* Experiment: measuring time to process a tweet to know how many tweets */
		/*			   a processor is capable of processing per unit of time	 */
		/*long millis = System.currentTimeMillis();*/
		
		Map<String, Double> res = null;
		try {
			res = analyzer.singleAnalysis(queriedTweet);
		} catch (Exception e) {
			logger.info(e.getMessage());
		}
		
		AnalyzedTweet analyzedTweet = new AnalyzedTweet();
		analyzedTweet.setQueriedTweet(queriedTweet);
		analyzedTweet.setAnalyticsResults(res);
		analyzedTweet.setAnalyzedBy(System.getProperty("analyzer"));
		
		rabbitTemplate.convertAndSend(toChooserExchangeName, queriedTweet.getMyQuery(), analyzedTweet);
		
		/*millis = System.currentTimeMillis() - millis;
		logger.info("Processed tweet in "+millis+" ms");*/
	}
}
