package org.geneontology.minerva.util;

import org.apache.log4j.Logger;

/**
 *
 *
 * @author dbk
 *
 */
public class DebugTools {
	private static Logger LOG = Logger.getLogger(DebugTools.class);
	private static Runtime runtime = Runtime.getRuntime();

	public static void logMemory(
		String title)
	{
		double bytesToMB = 1.0 / (double)(1024L*1024L);
		String statusText = "DebugTools[memory] " + title;

		runtime.gc();
		statusText += " freeMemory: " + (int) (runtime.freeMemory() * bytesToMB);
		LOG.info(statusText);
	}
}

