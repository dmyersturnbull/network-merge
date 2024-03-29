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
 * @author dmyersturnbull
 */
package org.structnetalign.weight;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.biojava.bio.structure.scop.ScopCategory;
import org.junit.Test;


public class ScopWeightTest {

	private static final double WEIGHT_PRECISION = 0.00001;
	
	@Test
	public void test() throws Exception {
		String cowSpermadhesin = "P29392";  // cow Acidic seminal fluid protein (ASFP) b.23.1.1; d1sfpa_
		String pigSpermadhesin = "P35495"; // pig Major seminal plasma glycoprotein PSP-I b.23.1.1; d1sppa_
		String histoCallogen = "Q9S0X0"; // Clostridium histolyticum Collagen-binding domain b.23.2.1; d1nqda_
		String yeastKiller = "P10410"; // Williopsis mrakii Yeast killer toxin b.11.1.2; d1wkta_
		String antiFungal = "Q9RCK8"; // Streptomyces tendae Antifungal protein AFP1 b.11.1.6; d1g6ea_
		ScopWeight weighter = new ScopWeight();
		Map<ScopCategory,Double> weights = ScopWeight.DEFAULT_WEIGHTS;
		assertEquals(weights.get(ScopCategory.Px).doubleValue(), weighter.assignWeight(0, 0, cowSpermadhesin, cowSpermadhesin), WEIGHT_PRECISION);
		assertEquals(weights.get(ScopCategory.Family).doubleValue(), weighter.assignWeight(0, 0, cowSpermadhesin, pigSpermadhesin), WEIGHT_PRECISION);
		assertEquals(weights.get(ScopCategory.Fold).doubleValue(), weighter.assignWeight(0, 0, cowSpermadhesin, histoCallogen), WEIGHT_PRECISION);
		assertEquals(weights.get(ScopCategory.Fold).doubleValue(), weighter.assignWeight(0, 0, pigSpermadhesin, histoCallogen), WEIGHT_PRECISION);
		assertEquals(weights.get(ScopCategory.Class).doubleValue(), weighter.assignWeight(0, 0, cowSpermadhesin, yeastKiller), WEIGHT_PRECISION);
		assertEquals(weights.get(ScopCategory.Class).doubleValue(), weighter.assignWeight(0, 0, cowSpermadhesin, antiFungal), WEIGHT_PRECISION);
		assertEquals(weights.get(ScopCategory.Superfamily).doubleValue(), weighter.assignWeight(0, 0, yeastKiller, antiFungal), WEIGHT_PRECISION);
	}

	@Test(expected=WeightException.class)
	public void testBadUniprotId() throws Exception {
		Map<ScopCategory, Double> weights = new HashMap<>();
		weights.put(ScopCategory.Fold, 0.1);
		weights.put(ScopCategory.Superfamily, 0.4);
		weights.put(ScopCategory.Family, 0.8);
		weights.put(ScopCategory.Domain, 1.0);
		ScopWeight weighter = new ScopWeight(weights);
		weighter.assignWeight(0, 1, "asdfasdfasdf", "sdgoyhljhsadf");
	}
	
}
