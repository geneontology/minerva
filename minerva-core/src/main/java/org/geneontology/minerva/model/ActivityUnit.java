/**
 * 
 */
package org.geneontology.minerva.model;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyAssertionAxiom;
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

	public ActivityUnit(OWLNamedIndividual ind, OWLOntology ont, GoCamModel model) {
		super(ind, ont, model);
		enablers = new HashSet<PhysicalEntity>();
		containing_processes = new HashSet<BiologicalProcessUnit>();
		causal_out = new HashMap<OWLObjectProperty, Set<GoCamOccurent>>();
		causal_in = new HashMap<OWLObjectProperty, Set<GoCamOccurent>>();
		inputs = new HashSet<PhysicalEntity>();
		outputs = new HashSet<PhysicalEntity>();
		locations = new HashSet<AnatomicalEntity>();

		Collection<OWLAxiom> ref_axioms = EntitySearcher.getReferencingAxioms(ind, ont);
		for(OWLAxiom axiom : ref_axioms) {
			if(axiom.getAxiomType().equals(AxiomType.OBJECT_PROPERTY_ASSERTION)) {
				OWLObjectPropertyAssertionAxiom a = (OWLObjectPropertyAssertionAxiom) axiom;
				OWLObjectProperty prop = (OWLObjectProperty) a.getProperty();
				if(a.getSubject().equals(ind)) {
					OWLNamedIndividual object = a.getObject().asOWLNamedIndividual();
					if(prop.getIRI().toString().equals("http://purl.obolibrary.org/obo/RO_0002333")) {
						enablers.add(new PhysicalEntity(object, ont, model));
					}
					else if(prop.getIRI().toString().equals("http://purl.obolibrary.org/obo/BFO_0000050")) {
						containing_processes.add(new BiologicalProcessUnit(object, ont, model));
					}else if(prop.getIRI().toString().equals("http://purl.obolibrary.org/obo/RO_0002233")) {
						inputs.add(new PhysicalEntity(object, ont, model));
					}else if(prop.getIRI().toString().equals("http://purl.obolibrary.org/obo/RO_0002234")) {
						outputs.add(new PhysicalEntity(object, ont, model));
					}
					else if(prop.getIRI().toString().equals("http://purl.obolibrary.org/obo/BFO_0000066")) {
						locations.add(new AnatomicalEntity(object, ont, model));
					}
					//all other properties now assumed to be causal relations 
					else {						
						GoCamOccurent object_event = getOccurent(object, ont, model);
						if(object_event!=null) {
							Set<GoCamOccurent> objects = causal_out.get(prop);
							if(objects==null) {
								objects = new HashSet<GoCamOccurent>();
							}
							objects.add(object_event);
							causal_out.put(prop, objects);
						}
					}
					//above we have annotations of this activity
					//here we check for causal relations linked to this activity from another one.  
				}else if(a.getObject().equals(ind)) {
					//the source 
					OWLNamedIndividual source = a.getSubject().asOWLNamedIndividual();
					GoCamEntity source_event = model.ind_entity.get(source);
					if(source_event==null) {
						source_event = getOccurent(source, ont, model);					
					}
					if(source_event !=null) {
						Set<GoCamOccurent> sources = causal_in.get(prop);
						if(sources==null) {
							sources = new HashSet<GoCamOccurent>();
						}
						sources.add((GoCamOccurent)source_event);
						causal_in.put(prop, sources);
					}
				}
			}
		}
	}	

	//causal_out = new HashMap<OWLObjectProperty, Set<GoCamOccurent>>();
	
	public Set<GoCamOccurent> getDownstream(GoCamOccurent activity){
		Set<GoCamOccurent> down = new HashSet<GoCamOccurent>();
		if(activity.causal_out!=null) {
			for(Set<GoCamOccurent> nextsteps : activity.causal_out.values()) {
				for(GoCamOccurent nextstep : nextsteps) {
					if(nextstep!=activity) {
						if(down.add(nextstep)) {
							Set<GoCamOccurent> morenextsteps = getDownstream(nextstep);
							down.addAll(morenextsteps);
						}
					}
				}
			}
		}
		return down;
	}
	
	
	private GoCamOccurent getOccurent(OWLNamedIndividual object, OWLOntology ont, GoCamModel model) {
		Set<String> types = model.ind_types.get(object);
		GoCamOccurent object_event = null;
		if(types.contains("http://purl.obolibrary.org/obo/GO_0008150")) {
			object_event = new BiologicalProcessUnit(object, ont, model);
		}else if(types.contains("http://purl.obolibrary.org/obo/GO_0003674")) {
			object_event = new ActivityUnit(object, ont, model);
		}
		return object_event;
	}
	
	public String toString() {
		String g = "";
		if(label!=null) {
			g += "label:"+label;
		}
		g+="\nIRI:"+individual.toString()+"\ntypes: "+this.stringForClasses(this.direct_types);
		if(comments!=null) {
			g+="\ncomments: "+comments+"\n";
		}
		if(notes!=null) {
			g+="\nnotes:"+notes;
		}
		if(enablers!=null) {
			g+="\nenabled by "+enablers;
		}
		if(locations!=null) {
			g+="\noccurs in "+locations;
		}
		if(containing_processes!=null) {
			g+="\npart of "+containing_processes;
		}
		if(inputs!=null) {
			g+="\nhas inputs "+inputs;
		}
		if(outputs!=null) {
			g+="\nhas outputs "+outputs;
		}
		return g;
	}
}
