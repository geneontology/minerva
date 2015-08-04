package org.geneontology.minerva.server;

import org.apache.log4j.Logger;
import org.glassfish.jersey.server.monitoring.ApplicationEvent;
import org.glassfish.jersey.server.monitoring.ApplicationEventListener;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.server.monitoring.RequestEventListener;

public class LoggingApplicationEventListener implements ApplicationEventListener {
	
	private static final Logger LOG = Logger.getLogger(LoggingApplicationEventListener.class);

	private volatile long requestCounter = 0;
	
	@Override
	public void onEvent(ApplicationEvent event) {
		switch (event.getType()) {
		case INITIALIZATION_FINISHED:
			LOG.info("Application " + event.getResourceConfig().getApplicationName()
					+ " initialization finished.");
			break;
		case DESTROY_FINISHED:
			LOG.info("Application "+ event.getResourceConfig().getApplicationName()+" destroyed.");
			break;
		default:
			break;
		}
	}

	@Override
	public RequestEventListener onRequest(RequestEvent requestEvent) {
		requestCounter++;
		LOG.info("Request " + requestCounter + " started.");
		return new LoggingRequestEventListener(requestCounter);
	}

	private static class LoggingRequestEventListener implements RequestEventListener {

		private final long requestNumber;
		private final long startTime;

		public LoggingRequestEventListener(long requestNumber) {
			this.requestNumber = requestNumber;
			startTime = System.currentTimeMillis();
		}

		@Override
		public void onEvent(RequestEvent event) {
			switch (event.getType()) {
			case RESOURCE_METHOD_START:
				LOG.info("Resource method " + event.getUriInfo().getMatchedResourceMethod().getHttpMethod()
						+ " started for request " + requestNumber);
				break;
			case FINISHED:
				LOG.info("Request " + requestNumber + " finished. Processing time "
						+ (System.currentTimeMillis() - startTime) + " ms.");
				break;
			default:
					break;
			}
			
		}

	}
}
