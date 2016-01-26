package org.geneontology.minerva.server.handler;

import java.util.List;
import java.util.Map;

import java.io.InputStream;
import java.io.InputStreamReader;

import javax.print.attribute.standard.Media;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

import org.geneontology.minerva.json.JsonAnnotation;
import org.geneontology.minerva.json.JsonEvidenceInfo;
import org.geneontology.minerva.json.JsonOwlFact;
import org.geneontology.minerva.json.JsonOwlIndividual;
import org.geneontology.minerva.json.JsonOwlObject;
import org.geneontology.minerva.json.JsonRelationInfo;

import com.google.gson.annotations.SerializedName;

@Path("/")
public class StatusHandler {
	public StatusHandler() {
	}

	public static class OfferingEntry {
		String 		name;
		String 		value;
	}

	public static class StatusResponse {

		String				message;
		String 				name;
		Boolean				okay;
		String 				location;
		String 				listening;

		@SerializedName("public")
		String 				publicLocation;

		OfferingEntry		offerings[];
	}

	@GET
	@Path("status")
	@Produces({MediaType.APPLICATION_JSON + ";charset=utf-8", "text/json"})
	public Response status(InputStream incomingData) {
 		Runtime runtime = Runtime.getRuntime();

		StatusResponse	statusResponse = new StatusResponse();
		statusResponse.message = "/status";
		statusResponse.okay = true;
		statusResponse.name = "Minerva";
		statusResponse.location = null;
		statusResponse.publicLocation = null;
		OfferingEntry 		o1 = new OfferingEntry();
		o1.name = "calls";
		o1.value = "0";
		statusResponse.offerings = new OfferingEntry[1];
		statusResponse.offerings[0] = o1;

		// return HTTP response 200 in case of success
		return Response.status(200).entity(statusResponse).build();
	}

	@GET
	@Path("status/memory")
	@Produces({MediaType.APPLICATION_JSON + ";charset=utf-8", "text/json"})
	public Response memstatus(InputStream incomingData) {
 		Runtime runtime = Runtime.getRuntime();

		// From https://en.wikipedia.org/wiki/Mebibyte
		// 	1 MiB = 220 bytes = 1024 kibibytes = 1048576bytes
 		long 			bytesPerMebibyte = 1048576;

 		long 			availableProcessors = runtime.availableProcessors();

 		long 			maxMemoryBefore = runtime.maxMemory() / bytesPerMebibyte;
 		long 			freeMemoryBefore = runtime.freeMemory() / bytesPerMebibyte;
 		long 			totalMemoryBefore = runtime.totalMemory() / bytesPerMebibyte;

 		runtime.gc();

 		long 			maxMemoryAfter = runtime.maxMemory() / bytesPerMebibyte;
 		long 			freeMemoryAfter = runtime.freeMemory() / bytesPerMebibyte;
 		long 			totalMemoryAfter = runtime.totalMemory() / bytesPerMebibyte;

 		String statusText = "";
 		statusText += " availableProcessors: " + availableProcessors + "\n";
 		statusText += " maxMemory, pre-gc :           " + maxMemoryBefore + " MiB\n";
 		statusText += " freeMemory, pre-gc :          " + freeMemoryBefore + " MiB\n";
 		statusText += " totalMemory, pre-gc :         " + totalMemoryBefore + " MiB\n";

 		statusText += " maxMemory, post-gc:           " + maxMemoryAfter + " MiB delta:" +
 			(maxMemoryAfter - maxMemoryBefore) + " MiB\n";
 		statusText += " freeMemory, post-gc:          " + freeMemoryAfter + " MiB delta:" +
 			(freeMemoryAfter - freeMemoryBefore) + " MiB\n";
 		statusText += " totalMemory, post-gc:         " + totalMemoryAfter + " MiB delta:" +
 			(totalMemoryAfter - totalMemoryBefore) + " MiB\n";

		StatusResponse	statusResponse = new StatusResponse();
		statusResponse.message = statusText;
		statusResponse.okay = true;
		statusResponse.name = "Minerva";
		statusResponse.location = null;
		statusResponse.publicLocation = null;
		// OfferingEntry 		o1 = new OfferingEntry();
		// o1.name = "calls";
		// o1.value = "0";
		// statusResponse.offerings = new OfferingEntry[1];
		// statusResponse.offerings[0] = o1;

		// return HTTP response 200 in case of success
		return Response.status(200).entity(statusResponse).build();
	}
}
