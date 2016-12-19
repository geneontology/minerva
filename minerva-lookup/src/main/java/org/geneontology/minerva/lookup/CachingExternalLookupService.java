package org.geneontology.minerva.lookup;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.semanticweb.owlapi.model.IRI;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.ExecutionError;
import com.google.common.util.concurrent.UncheckedExecutionException;

public class CachingExternalLookupService implements ExternalLookupService {
	
	private final LoadingCache<IRI, List<LookupEntry>> cache;
	private final ExternalLookupService service;
	
	private final static Logger LOG = Logger.getLogger(CachingExternalLookupService.class);
	
	public CachingExternalLookupService(ExternalLookupService service, int size, long duration, TimeUnit unit) {
		this.service = service;
		cache = CacheBuilder.newBuilder()
				.expireAfterWrite(duration, unit)
				.maximumSize(size)
				.build(new CacheLoader<IRI, List<LookupEntry>>() {

					@Override
					public List<LookupEntry> load(IRI key) throws Exception {
						return CachingExternalLookupService.this.service.lookup(key);
					}
				});
	}
	
	public CachingExternalLookupService(Iterable<ExternalLookupService> services, int size, long duration, TimeUnit unit) {
		this(new CombinedExternalLookupService(services), size, duration, unit);
	}

	public CachingExternalLookupService(int size, long duration, TimeUnit unit, ExternalLookupService...services) {
		this(Arrays.asList(services), size, duration, unit);
	}
	
	@Override
	public List<LookupEntry> lookup(IRI id) {
		try {
			return cache.get(id);
		} catch (ExecutionException e) {
			LOG.error("Could not lookup IRI: " + id, e);
			return Collections.emptyList();
		} catch (UncheckedExecutionException e) {
			LOG.error("Could not lookup IRI: " + id, e);
			return Collections.emptyList();
		} catch (ExecutionError e) {
			LOG.error("Could not lookup IRI: " + id, e);
			return Collections.emptyList();
		}
	}

	@Override
	public LookupEntry lookup(IRI id, String taxon) {
		LookupEntry entry = null;
		List<LookupEntry> list = cache.getUnchecked(id);
		for (LookupEntry current : list) {
			if (taxon.equals(current.taxon)) {
				entry = current;
				break;
			}
		}
		return entry;
	}

	@Override
	public String toString() {
		return "Caching("+service.toString()+")";
	}
	
	

}
