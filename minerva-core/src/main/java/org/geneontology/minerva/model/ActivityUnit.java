/**
 * 
 */
package org.geneontology.minerva.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyExpression;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.search.EntitySearcher;

import com.google.common.collect.Multimap;

/**
 * @author benjamingood
 *
 */
public class ActivityUnit extends GoCamOccurent{
	Set<BiologicalProcessUnit> containing_processes;
	Set<PhysicalEntity> enablers;
	
	public ActivityUnit(OWLNamedIndividual ind, OWLOntology ont, Map<OWLNamedIndividual, Set<String>> ind_types) {
		super(ind, ont);
		enablers = new HashSet<PhysicalEntity>();
		containing_processes = new HashSet<BiologicalProcessUnit>();
		causal_out = new HashMap<OWLObjectProperty, Set<GoCamOccurent>>();
		inputs = new HashSet<PhysicalEntity>();
		outputs = new HashSet<PhysicalEntity>();
		locations = new HashSet<AnatomicalEntity>();
		
		Multimap<OWLObjectPropertyExpression, OWLIndividual> prop_value = EntitySearcher.getObjectPropertyValues(ind, ont);
		for(OWLObjectPropertyExpression pe : prop_value.keySet()) {
			OWLObjectProperty prop = pe.asOWLObjectProperty();
			if(prop.getIRI().toString().equals("http://purl.obolibrary.org/obo/RO_0002333")) {
				for(OWLIndividual enabler_ind : prop_value.get(pe)) {
					enablers.add(new PhysicalEntity(enabler_ind.asOWLNamedIndividual(), ont));
				}
			}
			else if(prop.getIRI().toString().equals("http://purl.obolibrary.org/obo/BFO_0000050")) {
				for(OWLIndividual process_ind : prop_value.get(pe)) {
					containing_processes.add(new BiologicalProcessUnit(process_ind.asOWLNamedIndividual(), ont));
				}
			}else if(prop.getIRI().toString().equals("http://purl.obolibrary.org/obo/RO_0002233")) {
				for(OWLIndividual input_ind : prop_value.get(pe)) {
					inputs.add(new PhysicalEntity(input_ind.asOWLNamedIndividual(), ont));
				} //http://purl.obolibrary.org/obo/RO_0002234 
			}else if(prop.getIRI().toString().equals("http://purl.obolibrary.org/obo/RO_0002234")) {
				for(OWLIndividual output_ind : prop_value.get(pe)) {
					outputs.add(new PhysicalEntity(output_ind.asOWLNamedIndividual(), ont));
				} 
			}
			else if(prop.getIRI().toString().equals("http://purl.obolibrary.org/obo/BFO_0000066")) {
				for(OWLIndividual loc_ind : prop_value.get(pe)) {
					locations.add(new AnatomicalEntity(loc_ind.asOWLNamedIndividual(), ont));
				} 
			}
			else {
				for(OWLIndividual object : prop_value.get(pe)) {
					OWLNamedIndividual n_object = object.asOWLNamedIndividual();
					Set<String> types = ind_types.get(n_object);
					GoCamOccurent object_event = null;
					if(types.contains("http://purl.obolibrary.org/obo/GO_0008150")) {
						object_event = new BiologicalProcessUnit(n_object, ont);
					}else if(types.contains("http://purl.obolibrary.org/obo/GO_0003674")) {
						object_event = new ActivityUnit(n_object, ont, ind_types);
					}
					Set<GoCamOccurent> objects = causal_out.get(prop);
					if(objects==null) {
						objects = new HashSet<GoCamOccurent>();
					}
					objects.add(object_event);
					causal_out.put(prop, objects);
				}
			}
			
		}
	}	
}
