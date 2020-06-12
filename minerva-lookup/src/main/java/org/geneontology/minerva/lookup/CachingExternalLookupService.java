package org.geneontology.minerva.lookup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
						List<LookupEntry> lookup = CachingExternalLookupService.this.service.lookup(key);
						if (lookup == null || lookup.isEmpty()) {
							throw new Exception("No legal value for key.");
						}
						return lookup;
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

	@Override
	public Map<IRI, List<LookupEntry>> lookupBatch(Set<IRI> to_look_up) {
		try {
			Map<IRI, List<LookupEntry>> id_lookups = cache.getAll(to_look_up);
			return id_lookups;
		} catch (ExecutionException e) {
			return null;
		} catch (UncheckedExecutionException e) {
			return null;
		} catch (ExecutionError e) {
			return null;
		}
	}
	
	

}
