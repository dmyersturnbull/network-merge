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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.structnetalign.PipelineProperties;

import psidev.psi.mi.xml.PsimiXmlReader;
import psidev.psi.mi.xml.PsimiXmlReaderException;
import psidev.psi.mi.xml.PsimiXmlVersion;
import psidev.psi.mi.xml.PsimiXmlWriter;
import psidev.psi.mi.xml.PsimiXmlWriterException;
import psidev.psi.mi.xml.model.Attribute;
import psidev.psi.mi.xml.model.AttributeContainer;
import psidev.psi.mi.xml.model.Confidence;
import psidev.psi.mi.xml.model.DbReference;
import psidev.psi.mi.xml.model.Entry;
import psidev.psi.mi.xml.model.EntrySet;
import psidev.psi.mi.xml.model.Interaction;
import psidev.psi.mi.xml.model.Interactor;
import psidev.psi.mi.xml.model.Names;
import psidev.psi.mi.xml.model.Participant;
import psidev.psi.mi.xml.model.PsiFactory;
import psidev.psi.mi.xml.model.Unit;
import psidev.psi.mi.xml.model.Xref;
import edu.uci.ics.jung.graph.util.Pair;

/**
 * A collection of static utilities related to PSI-MI XML files and data.
 * @author dmyersturnbull
 *
 */
public class NetworkUtils {

	public static final String NEWLINE;

	public static final PsimiXmlVersion XML_VERSION = PsimiXmlVersion.VERSION_254;
	private static final Logger logger = LogManager.getLogger("org.structnetalign");
	private final static String UNIPROT_ACCESSION = "MI:0486";

	private final static String UNIPROT_ACCESSION_TYPE = "MI:0356";

	static {
		NEWLINE = System.getProperty("line.separator");
	}

	public static Double getExistingAnnotationValue(AttributeContainer container, String attributeName) {
		Attribute attribute = getExistingAnnotation(container, attributeName);
		if (attribute != null && attribute.getValue() != null) {
			return Double.parseDouble(attribute.getName());
		}
		return null;
	}
	
	public static Attribute getExistingAnnotation(AttributeContainer container, String attributeName) {
		for (Attribute attribute : container.getAttributes()) {
			if (attributeName.equals(attribute.getName())) {
				return attribute;
			}
		}
		return null;
	}

	/**
	 * Finds a confidence with label {@code confidenceLabel} <strong>or</strong> full name
	 * {@code confidenceFullName}.
	 * @param interaction
	 * @param confidenceLabel
	 * @param confidenceFullName
	 * @return The numerical value of the Confidence if it exists; null otherwise
	 */
	public static Double getExistingConfidenceValue(Interaction interaction, String confidenceLabel,
			String confidenceFullName) {
		Confidence conf = getExistingConfidence(interaction, confidenceLabel, confidenceFullName);
		if (conf != null && conf.getValue() != null) {
			return Double.parseDouble(conf.getValue());
		}
		return null;
	}

	/**
	 * Returns the first Confidence with <em>either</em> label {@code confidenceLabel} <strong>or</strong> full name
	 * {@code confidenceFullName}.
	 * @return The Confidence if it exists; null otherwise
	 */
	public static Confidence getExistingConfidence(Interaction interaction, String confidenceLabel,
			String confidenceFullName) {

		for (Confidence exisiting : interaction.getConfidences()) {
			Unit existingUnit = exisiting.getUnit();
			if (existingUnit != null) {
				Names existingNames = existingUnit.getNames();
				if (existingNames != null) {
					if (confidenceLabel.equals(existingNames.getShortLabel())
							|| confidenceFullName.equals(existingNames.getFullName())) {
						return exisiting;
					}
				}
			}
		}
		return null;

	}

	/**
	 * Returns the Interactors acting as Participants in {@code interaction}.
	 * @throws IllegalArgumentException If {@link interaction} does not contain exactly 2 participants
	 */
	public static Pair<Interactor> getInteractors(Interaction interaction) {
		Collection<Participant> participants = interaction.getParticipants();
		if (participants.size() != 2) throw new IllegalArgumentException(
				"Cannot handle interactions involving more than 2 participants");
		Iterator<Participant> iter = participants.iterator();
		Pair<Interactor> pair = new Pair<>(iter.next().getInteractor(), iter.next().getInteractor());
		return pair;
	}

	/**
	 * Returns the UniProt Ids of the the Interactors acting as Participants in {@code interaction}.
	 * @throws IllegalArgumentException If {@link interaction} does not contain exactly 2 participants
	 */
	public static Pair<String> getUniProtId(Interaction interaction) {
		Pair<Interactor> interactors = getInteractors(interaction);
		String uniProtId1 = getUniProtId(interactors.getFirst());
		if (uniProtId1 == null) throw new IllegalArgumentException("Couldn't find UniProt Id for " + interactors.getFirst().getId());
		String uniProtId2 = getUniProtId(interactors.getSecond());
		if (uniProtId2 == null) throw new IllegalArgumentException("Couldn't find UniProt Id for " + interactors.getSecond().getId());
		Pair<String> pair = new Pair<>(uniProtId1, uniProtId2);
		return pair;
	}

	/**
	 * Returns the UniProtId corresponding to this interactor, or null if it doesn't exist.
	 */
	public static String getUniProtId(Interactor interactor) {
		Xref xref = interactor.getXref();
		if (xref != null) {
			Collection<DbReference> refs = xref.getAllDbReferences();
			for (DbReference ref : refs) {
				if (UNIPROT_ACCESSION.equals(ref.getDbAc()) && UNIPROT_ACCESSION_TYPE.equals(ref.getRefTypeAc())) {
					return ref.getId();
				}
			}
		}
		return null;
	}

	/**
	 * @return A map of the Ids of each interactor in {@code entrySet} to their UniProt Ids; note that this map is <em>not</em> injective
	 */
	public static Map<Integer, String> getUniProtIds(EntrySet entrySet) {
		HashMap<Integer, String> map = new HashMap<>();
		for (Entry entry : entrySet.getEntries()) {
			for (Interactor interactor : entry.getInteractors()) {
				String id = getUniProtId(interactor);
				if (id != null) {
					map.put(interactor.getId(), id);
				} else {
					logger.warn("Reference Id not found for interactor #" + interactor.getId() + " (using acession "
							+ UNIPROT_ACCESSION + " of type " + UNIPROT_ACCESSION_TYPE + "). Not including.");
				}
			}
		}
		return map;
	}

	/**
	 * @return A map of the Ids of each interactor in the PSI-MI file {@code file} to their UniProt Ids; note that this map is <em>not</em> injective
	 */
	public static Map<Integer, String> getUniProtIds(File file) {
		return getUniProtIds(NetworkUtils.readNetwork(file));
	}

	public static NavigableSet<Integer> getVertexIds(Interaction interaction) {
		Collection<Participant> participants = interaction.getParticipants();
		if (participants.size() != 2) throw new IllegalArgumentException(
				"Cannot handle interactions involving more than 2 participants");
		NavigableSet<Integer> set = new TreeSet<>();
		for (Participant participant : participants) {
			int id = participant.getInteractor().getId();
			set.add(id);
		}
		return set;
	}

	/**
	 * Returns an MD5 hash of the element {@link #toString() toString()s} of {@code collection}.
	 * @throws IllegalArgumentException If {@code collection} contains an element whose {@link #toString()} contains a semicolon
	 */
	@SuppressWarnings("rawtypes")
	public static String hash(Collection collection) {
		MessageDigest md;
		try {
			md = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("Couldn't find the algorithm MD5", e);
		}
		StringBuilder sb = new StringBuilder();
		for (Object neighbor : collection) {
			if (neighbor.toString().contains(";")) throw new IllegalArgumentException(
					"String cannot contain a semicolon");
			sb.append(neighbor.toString() + ";");
		}
		byte[] bytes = md.digest(sb.toString().getBytes());
		return new String(Hex.encodeHex(bytes));
	}

	/**
	 * Returns an MD5 hash of the element {@link #toString() toString()s} of {@code array}.
	 * @throws IllegalArgumentException If {@code array} contains an element whose {@link #toString()} contains a semicolon
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static String hash(Object... array) {
		List list = new ArrayList();
		for (Object object : array) {
			list.add(object);
		}
		return hash(list);
	}

	public static Confidence makeConfidence(double value, String confidenceLabel, String confidenceFullName,
			String xRefId) {
		String theValue = PipelineProperties.getInstance().getOutputFormatter().format(value);
		Confidence confidence = PsiFactory.createConfidence(theValue, xRefId, confidenceLabel);
		confidence.getUnit().getNames().setFullName(confidenceFullName);
		return confidence;
	}

	public static EntrySet readNetwork(File file) {
		PsimiXmlReader reader = new PsimiXmlReader(XML_VERSION);
		EntrySet entrySet;
		try {
			entrySet = reader.read(file);
		} catch (PsimiXmlReaderException e) {
			throw new RuntimeException("Couldn't parse input file " + file.getPath(), e);
		}
		return entrySet;
	}

	public static EntrySet readNetwork(String string) {
		return readNetwork(new File(string));
	}

	/**
	 * @return {@code s} repeated {@code n} times
	 */
	public static String repeat(String s, int n) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < n; i++) {
			sb.append(s);
		}
		return sb.toString();
	}

	/**
	 * @return A new Entry with the same Source, Attributes, Availabilities, and Experiments, but no Interactions or Interactors
	 */
	public static Entry skeletonClone(Entry entry) {
		Entry myEntry = new Entry();
		myEntry.setSource(entry.getSource());
		myEntry.getAttributes().addAll(entry.getAttributes());
		myEntry.getAvailabilities().addAll(entry.getAvailabilities());
		myEntry.getExperiments().addAll(entry.getExperiments());
		return myEntry;
	}

	/**
	 * @return A new EntrySet with the same version, minor version, and level
	 */
	public static EntrySet skeletonClone(EntrySet entrySet) {
		EntrySet myEntrySet = new EntrySet();
		myEntrySet.setVersion(entrySet.getVersion());
		myEntrySet.setMinorVersion(entrySet.getMinorVersion());
		myEntrySet.setLevel(entrySet.getLevel());
		return myEntrySet;
	}

	/**
	 * Converts space indentation to tab indentation, assuming no lines have trailing whitespace.
	 * Useful for keeping file size down.
	 */
	public static void spacesToTabs(File input, File output, int nSpaces) throws IOException {
		BufferedReader br = new BufferedReader(new FileReader(input));
		PrintWriter pw = new PrintWriter(output);
		String line = "";
		while ((line = br.readLine()) != null) {
			String trimmed = line.trim();
			int indent = (int) ((float) (line.length() - trimmed.length()) / (float) nSpaces);
			pw.println(repeat("\t", indent) + trimmed);
		}
		br.close();
		pw.close();
	}

	/**
	 * Converts space indentation to tab indentation, assuming no lines have trailing whitespace.
	 * Useful for keeping file size down.
	 */
	public static String spacesToTabs(String input, int nSpaces) throws IOException {
		StringBuilder sb = new StringBuilder();
		String[] lines = input.split(NEWLINE);
		for (String line : lines) {
			String trimmed = line.trim();
			int indent = (int) ((float) (line.length() - trimmed.length()) / (float) nSpaces);
			sb.append(repeat("\t", indent) + trimmed + NEWLINE);
		}
		return sb.toString();
	}

	public static void writeNetwork(EntrySet entrySet, File file) {

		logger.info("Writing network to " + file);

		PsimiXmlWriter psimiXmlWriter = new PsimiXmlWriter(XML_VERSION);
		try {
			psimiXmlWriter.write(entrySet, file);
		} catch (PsimiXmlWriterException e) {
			throw new RuntimeException("Couldn't write XML to " + file.getPath(), e);
		}

		// to reduce file size
		File tmp = new File(file + ".spaces.xml.tmp");
		try {
			FileUtils.moveFile(file, tmp);
			spacesToTabs(tmp, file, 4);
			tmp.delete();
		} catch (IOException e) {
			logger.warn("Could not convert spaces in " + file.getPath() + " to tabs", e);
		}

		logger.info("Wrote network to " + file);

	}

	public static void writeNetwork(EntrySet entrySet, String file) {
		writeNetwork(entrySet, new File(file));
	}

}
