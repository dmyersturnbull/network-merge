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
package org.structnetalign.weight;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.biojava.bio.structure.scop.ScopCategory;
import org.biojava.bio.structure.scop.ScopDatabase;
import org.biojava.bio.structure.scop.ScopDomain;
import org.structnetalign.util.BasicScop;
import org.structnetalign.util.IdentifierMappingFactory;

/**
 * A {@link Weight} that uses the <a href="http://scop.berkeley.edu">Structural Classification of Proteins</a> to
 * determine weights.
 * 
 * @author dmyersturnbull
 * 
 */
public class ScopWeight implements RelationWeight {

	public static final Map<ScopCategory, Double> DEFAULT_WEIGHTS = new HashMap<ScopCategory, Double>();

	private String scopId1;

	private String scopId2;
	private String uniProtId1;

	private String uniProtId2;
	private int v1;

	private int v2;
	private Map<ScopCategory, Double> weights;

	static {
		Properties props = new Properties();
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		InputStream stream = loader.getResourceAsStream("weight/scop_weights.properties");
		try {
			props.load(stream);
		} catch (IOException e) {
			throw new RuntimeException("Couldn't open SCOP weights property file", e);
		}
		DEFAULT_WEIGHTS.put(ScopCategory.Class, Double.parseDouble(props.getProperty("cl")));
		DEFAULT_WEIGHTS.put(ScopCategory.Fold, Double.parseDouble(props.getProperty("cf")));
		DEFAULT_WEIGHTS.put(ScopCategory.Superfamily, Double.parseDouble(props.getProperty("sf")));
		DEFAULT_WEIGHTS.put(ScopCategory.Family, Double.parseDouble(props.getProperty("fa")));
		DEFAULT_WEIGHTS.put(ScopCategory.Px, Double.parseDouble(props.getProperty("px")));
		DEFAULT_WEIGHTS.put(ScopCategory.Species, Double.parseDouble(props.getProperty("sp")));
		DEFAULT_WEIGHTS.put(ScopCategory.Domain, Double.parseDouble(props.getProperty("dm")));
	}

	/**
	 * Thread safety is required here.
	 * 
	 * @return
	 */
	private static synchronized ScopDatabase getSCOP() {
		return BasicScop.getScop();
	}

	private static int sunIdOfCategory(ScopDomain domain, ScopCategory category) {
		switch (category) {
		case Class:
			return domain.getClassId();
		case Fold:
			return domain.getFoldId();
		case Superfamily:
			return domain.getSuperfamilyId();
		case Family:
			return domain.getFamilyId();
		case Domain:
			return domain.getDomainId();
		case Px:
			return domain.getPx();
		case Species:
			return domain.getSpeciesId();
		default:
			throw new IllegalArgumentException("Invalid SCOP category " + category.name());
		}
	}

	public ScopWeight() {
		this(DEFAULT_WEIGHTS);
	}

	public ScopWeight(Map<ScopCategory, Double> weights) {
		this.weights = weights;
	}

	@Override
	public double assignWeight(int v1, int v2, String uniProtId1, String uniProtId2) throws Exception {
		setIds(v1, v2, uniProtId1, uniProtId2);
		return call().getWeight();
	}

	@Override
	public WeightResult call() throws Exception {

		final ScopDatabase scop = ScopWeight.getSCOP();
		ScopDomain domain1 = scop.getDomainByScopID(scopId1);
		if (domain1 == null) throw new WeightException("Could not find SCOP id for " + uniProtId1, v1, v2, uniProtId1,
				uniProtId2, false, true);
		ScopDomain domain2 = scop.getDomainByScopID(scopId2);
		if (domain2 == null) throw new WeightException("Could not find SCOP id for " + uniProtId2, v1, v2, uniProtId1,
				uniProtId2, false, true);

		// we need to iterate in reverse order (most specific first)
		ScopCategory[] categories = ScopCategory.values();
		Collections.reverse(Arrays.asList(categories));
		for (ScopCategory category : categories) {
			int categoryId1 = sunIdOfCategory(domain1, category);
			int categoryId2 = sunIdOfCategory(domain2, category);
			if (categoryId1 == categoryId2 && weights.get(category) != null) {
				return new WeightResult(weights.get(category), v1, v2, uniProtId1, uniProtId2, this.getClass());
			}
		}

		return new WeightResult(0.0, v1, v2, uniProtId1, uniProtId2, this.getClass());

	}

	@Override
	public void setIds(int v1, int v2, String uniProtId1, String uniProtId2) throws WeightException {

		this.v1 = v1;
		this.v2 = v2;
		this.uniProtId1 = uniProtId1;
		this.uniProtId2 = uniProtId2;

		scopId1 = IdentifierMappingFactory.getMapping().uniProtToScop(uniProtId1);
		if (scopId1 == null) throw new WeightException("Could not find SCOP id for " + uniProtId1, v1, v2, uniProtId1,
				uniProtId2, false, true);
		scopId2 = IdentifierMappingFactory.getMapping().uniProtToScop(uniProtId2);
		if (scopId2 == null) throw new WeightException("Could not find SCOP id for " + uniProtId2, v1, v2, uniProtId1,
				uniProtId2, false, true);

	}

}
