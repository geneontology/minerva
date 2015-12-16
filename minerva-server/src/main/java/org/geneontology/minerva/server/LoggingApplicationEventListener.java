package org.geneontology.minerva.server;

import java.io.IOException;
import java.util.List;
import java.util.Map.Entry;

import javax.ws.rs.core.MultivaluedMap;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.apache.tools.ant.filters.StringInputStream;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ExtendedUriInfo;
import org.glassfish.jersey.server.model.ResourceMethod;
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
				ExtendedUriInfo uriInfo = event.getUriInfo();
				ResourceMethod method = uriInfo.getMatchedResourceMethod();
				ContainerRequest containerRequest = event.getContainerRequest();
				LOG.info(requestNumber+" Resource method " + method.getHttpMethod() + " started for request " + requestNumber);
				LOG.info(requestNumber+" Headers: "+ render(containerRequest.getHeaders()));
				LOG.info(requestNumber+" Path: "+uriInfo.getPath());
				LOG.info(requestNumber+" PathParameters: "+ render(uriInfo.getPathParameters()));
				LOG.info(requestNumber+" QueryParameters: "+ render(uriInfo.getQueryParameters()));
				LOG.info(requestNumber+" Body: "+getBody(containerRequest));
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
	
	private static CharSequence getBody(ContainerRequest request) {
		String body = null;
		try {
			body = IOUtils.toString(request.getEntityStream());
			// reading the stream consumes it, need to re-create it for the real thing
			request.setEntityStream(new StringInputStream(body)); 
		} catch (IOException e) {
			LOG.warn("Couldn't ready body.", e);
		}
		return body;
	}
	
	private static CharSequence render(MultivaluedMap<String, String> map) {
		StringBuilder sb = new StringBuilder();
		int count = 0;
		sb.append('[');
		for (Entry<String, List<String>> entry : map.entrySet()) {
			if (count > 0) {
				sb.append(',');
			}
			sb.append('{').append(entry.getKey()).append(',').append(entry.getValue()).append('}');
			count += 1;
		}
		sb.append(']');
		return sb;
	}
}
