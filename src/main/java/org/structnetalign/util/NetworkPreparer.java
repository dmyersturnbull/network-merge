/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE
 * file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 * 
 * @author dmyersturnbull
 */
package org.structnetalign.util;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.structnetalign.InteractionEdge;
import org.structnetalign.PipelineProperties;

import psidev.psi.mi.xml.model.Confidence;
import psidev.psi.mi.xml.model.Entry;
import psidev.psi.mi.xml.model.EntrySet;
import psidev.psi.mi.xml.model.ExperimentDescription;
import psidev.psi.mi.xml.model.Interaction;
import psidev.psi.mi.xml.model.InteractionDetectionMethod;
import psidev.psi.mi.xml.model.Interactor;
import psidev.psi.mi.xml.model.Names;
import psidev.psi.mi.xml.model.Participant;
import psidev.psi.mi.xml.model.Source;
import edu.uci.ics.jung.algorithms.cluster.WeakComponentClusterer;
import edu.uci.ics.jung.graph.UndirectedGraph;
import edu.uci.ics.jung.graph.util.Pair;

/**
 * A standalone utility to simplify a PSI-MI XML file by removing information that is not usable in the pipeline.
 * This reduces the sizes of files.
 * Also assigns initial confidence values to interactions.
 * This <em>should</em> be run before running on any network, although this is not strictly required.
 * @author dmyersturnbull
 *
 */
public class NetworkPreparer {

	private static final Logger logger = LogManager.getLogger("org.structnetalign");
	
	/**
	 * Method-based confidence.
	 * Aka PMID:19420069 or method score
	 */
	private static final String CONFIDENCE_XREF = "MI:1072";
	
	public static void main(String[] args) {
		if (args.length != 2) {
			System.err.println("Usage: NetworkPreparer input-file output-dir");
			return;
		}
		NetworkPreparer preparer = new NetworkPreparer();
		preparer.prepare(new File(args[0]), new File(args[1]));
	}

	@Deprecated
	public List<EntrySet> getConnnectedComponents(EntrySet entrySet) {

		// first, find the connected components as sets of vertices
		UndirectedGraph<Integer, InteractionEdge> graph = GraphInteractionAdaptor.toGraph(entrySet);
		logger.info("There are " + graph.getVertexCount() + " vertices and " + graph.getEdgeCount() + " edges");
		WeakComponentClusterer<Integer, InteractionEdge> alg = new WeakComponentClusterer<>();
		Set<Set<Integer>> ccs = alg.transform(graph);
		logger.info("Found " + ccs.size() + " connected components");

		// map each vertex id to an interactor and a set of interactions
		HashMap<Integer, Interactor> interactorMap = new HashMap<>();
		HashMap<Integer, Set<Interaction>> interactionMap = new HashMap<>();
		for (Entry entry : entrySet.getEntries()) {
			for (Interactor interactor : entry.getInteractors()) {
				interactorMap.put(interactor.getId(), interactor);
				interactionMap.put(interactor.getId(), new HashSet<Interaction>());
			}
			for (Interaction interaction : entry.getInteractions()) {
				NavigableSet<Integer> participants = NetworkUtils.getVertexIds(interaction);
				for (int participant : participants) {
					Set<Interaction> interactionsOfInteractor = interactionMap.get(participant);
					interactionsOfInteractor.add(interaction);
				}
			}
		}

		// the schema requires a non-null source
		Source source = null;
		for (Entry entry : entrySet.getEntries()) {
			if (entry.getSource() != null) {
				source = entry.getSource();
				break;
			} else {
				logger.warn("An entry is missing a source");
			}
		}
		
		// now create an EntrySet per cc
		List<EntrySet> myEntrySets = new ArrayList<>(ccs.size());
		for (Set<Integer> cc : ccs) {
			EntrySet myEntrySet = NetworkUtils.skeletonClone(entrySet);
			Entry myEntry = new Entry();
			myEntry.setSource(source);
			for (int id : cc) {
				Interactor interactor = interactorMap.get(id);
				Set<Interaction> interactions = interactionMap.get(id);
				myEntry.getInteractors().add(interactor);
				myEntry.getInteractions().addAll(interactions);
			}
			myEntrySet.getEntries().add(myEntry);
			myEntrySets.add(myEntrySet);
		}

		return myEntrySets;

	}

	public void prepare(File input, File output) {
		EntrySet entrySet = NetworkUtils.readNetwork(input);
		entrySet = simplify(entrySet);
		entrySet = initConfidences(entrySet, PipelineProperties.getInstance().getInitialConfLabel(), PipelineProperties.getInstance().getInitialConfName());
		NetworkUtils.writeNetwork(entrySet, output);
	}

	private double getWeight(Interaction interaction) {
		Collection<ExperimentDescription> experiments = interaction.getExperiments();
		double weight = 0;
		for (ExperimentDescription experiment : experiments) {
			InteractionDetectionMethod method = experiment.getInteractionDetectionMethod();
			if (method != null) {
				Names theNames = method.getNames();
				if (theNames != null) {
					double newWeight = Experiments.getInstance().getWeight(theNames.getFullName());
					weight += newWeight - weight*newWeight;
				}
			}
		}
		if (weight > 0) return weight;
		return  Experiments.getInstance().getWeight(null);
	}
	
	/**
	 * Gives a new confidence to each interaction based on its number of occurrences.
	 * Specifically, the confidence value is the probability of any interaction given that each single interaction is assigned a probability of {@code p0}.
	 */
	public EntrySet initConfidences(EntrySet entrySet, String confidenceLabel, String confidenceFullName) {

		// first, only copy the essential information (e.g. version number)
		EntrySet myEntrySet = new EntrySet();
		myEntrySet.setVersion(entrySet.getVersion());
		myEntrySet.setMinorVersion(entrySet.getMinorVersion());
		myEntrySet.setLevel(entrySet.getLevel());

		HashSet<Pair<Integer>> exisitingEdges = new HashSet<>();
		
		Map<Pair<Integer>,Confidence> confidences = new HashMap<>();

		int entryIndex = 1;
		for (Entry entry : entrySet.getEntries()) {

			logger.info("Setting initial confidences in entry " + entryIndex);

			Entry myEntry = new Entry();
			myEntry.setSource(entry.getSource());
			Collection<Interaction> myInteractions = myEntry.getInteractions();
			
			for (Interaction interaction : entry.getInteractions()) {
				
				NavigableSet<Integer> participants = NetworkUtils.getVertexIds(interaction);
				Pair<Integer> pair = new Pair<>(participants.first(), participants.last());
				
				double experimentWeight = getWeight(interaction);
				
				if (exisitingEdges.contains(pair)) {
					
					double prevValue = Double.parseDouble(confidences.get(pair).getValue());
					double newValue = prevValue + experimentWeight - prevValue * experimentWeight;
					confidences.get(pair).setValue(PipelineProperties.getInstance().getOutputFormatter().format(newValue));
					logger.debug("Updated initial confidence of interaction Id#" + interaction.getId() + " from " + PipelineProperties.getInstance().getDisplayFormatter().format(prevValue) + " to " + PipelineProperties.getInstance().getDisplayFormatter().format(newValue));
					
				} else {
					
					exisitingEdges.add(pair);
					myInteractions.add(interaction);

					// a confidence with this label or full name shouldn't already exist
					// if it does, it probably means we've already run before
					// so, we'll just delete it from the output
					// it can always be recovered from the input file
					// but this is critical because otherwise we'd lose our new confidence
					Confidence alreadyExists = NetworkUtils.getExistingConfidence(interaction, confidenceLabel, confidenceFullName);
					if (alreadyExists != null) {
						logger.warn("Confidence " + confidenceLabel + " already exists. Overwriting.");
						interaction.getConfidences().remove(alreadyExists);
					}

					// make a new Confidence
					Confidence confidence = NetworkUtils.makeConfidence(experimentWeight, confidenceLabel, confidenceFullName, CONFIDENCE_XREF);

					confidences.put(pair, confidence);
					logger.debug("Set initial confidence of interaction Id#" + interaction.getId() + " to " + experimentWeight);
				}
				
				entryIndex++;
			}
			
			myEntry.getInteractors().addAll(entry.getInteractors());
			myEntrySet.getEntries().add(myEntry);
		}
		
		// now add the confidences
		for (Entry entry : myEntrySet.getEntries()) {
			for (Interaction interaction : entry.getInteractions()) {
				NavigableSet<Integer> participants = NetworkUtils.getVertexIds(interaction);
				Pair<Integer> pair = new Pair<>(participants.first(), participants.last());
				Confidence conf = confidences.get(pair);
				interaction.getConfidences().add(conf);
			}
		}

		return myEntrySet;
	}

	/**
	 * Removes multimolecular and unimolecular interactions from the EntrySet, and removes information about experiments, etc.
	 * @param entrySet
	 * @return
	 */
	public EntrySet simplify(EntrySet entrySet) {

		// first, only copy the essential information (e.g. version number)
		EntrySet myEntrySet = NetworkUtils.skeletonClone(entrySet);

		int numInteractionsInit = 0;
		int numInteractionsRemoved = 0;

		// now we want to add only the interactions and interactors from each entry
		for (Entry entry : entrySet.getEntries()) {

			numInteractionsInit += entry.getInteractions().size();

			Entry myEntry = NetworkUtils.skeletonClone(entry);

			// only include interactions that have exactly 2 participants
			for (Interaction interaction : entry.getInteractions()) {

				Collection<Participant> participants = interaction.getParticipants();
				if (participants.size() == 2) {
					myEntry.getInteractions().add(interaction);
				} else {
					numInteractionsRemoved++;
					logger.debug("Removing interaction " + interaction.getId() + " because it has " + participants.size() + " participants");
				}
			}

			myEntry.getInteractors().addAll(entry.getInteractors());
			myEntrySet.getEntries().add(myEntry);
			
			logger.info("Removed " + numInteractionsRemoved + " interactions out of " + numInteractionsInit);

		}

		return myEntrySet;

	}

}
