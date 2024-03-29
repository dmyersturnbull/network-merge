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

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.biojava.bio.structure.scop.ScopCategory;
import org.junit.Test;
import org.structnetalign.HomologyEdge;

import edu.uci.ics.jung.graph.UndirectedGraph;



public class SimpleWeightManagerTest {

	private static final double PRECISION = 0.001;
	
	@Test
	public void test() {
		SimpleWeightManager manager = new SimpleWeightManager();
		manager.add(new CeWeight(), 1);
		Map<ScopCategory, Double> weights = new HashMap<>();
		weights.put(ScopCategory.Fold, 0.1);
		weights.put(ScopCategory.Superfamily, 0.4);
		weights.put(ScopCategory.Family, 0.8);
		weights.put(ScopCategory.Domain, 1.0);
		ScopWeight scop = new ScopWeight(weights);
		manager.add(scop, 1);
//		manager.add(new NeedlemanWunschWeight(), 1);
		UndirectedGraph<Integer,HomologyEdge> hom = WeightManagerTest.testSimple(manager);
		assertEquals("Wrong number of homology edges", 15, hom.getEdgeCount());
		
		// gets an alignment score of 0.507 and a SCOP score of 0.4
		assertEquals(0.6988820186030443, hom.findEdge(4, 5).getWeight(), PRECISION);
	}
	
}
