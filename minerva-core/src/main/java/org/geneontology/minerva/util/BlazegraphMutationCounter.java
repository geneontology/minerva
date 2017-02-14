package org.geneontology.minerva.util;

import com.bigdata.rdf.changesets.IChangeLog;
import com.bigdata.rdf.changesets.IChangeRecord;

public class BlazegraphMutationCounter implements IChangeLog {
	
	private int records = 0;
	
	public int mutationCount() {
		return records;
	}

	@Override
	public void changeEvent(IChangeRecord record) {
		records++;
	}

	@Override
	public void close() {}

	@Override
	public void transactionAborted() {}

	@Override
	public void transactionBegin() {}

	@Override
	public void transactionCommited(long commitTime) {}

	@Override
	public void transactionPrepare() {}

}
