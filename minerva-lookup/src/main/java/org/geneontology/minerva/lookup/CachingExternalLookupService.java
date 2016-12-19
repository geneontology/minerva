package org.geneontology.minerva.lookup;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.semanticweb.owlapi.model.IRI;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.ExecutionError;
import com.google.common.util.concurrent.UncheckedExecutionException;

public class CachingExternalLookupService implements ExternalLookupService {
	
	private final LoadingCache<IRI, List<LookupEntry>> cache;
	private final ExternalLookupService service;
	
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
			return null;
		} catch (UncheckedExecutionException e) {
			return null;
		} catch (ExecutionError e) {
			return null;
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
