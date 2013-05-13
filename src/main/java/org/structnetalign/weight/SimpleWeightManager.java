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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.structnetalign.CleverGraph;
import org.structnetalign.HomologyEdge;

/**
 * A simple {@link WeightManager} that keeps a list of {@link Weight Weights} and sums over each weight. If a Weight
 * fails, it simply adds 0.
 * 
 * @author dmyersturnbull
 * 
 */
public class SimpleWeightManager implements WeightManager {

	private List<Double> coefficients;
	private double threshold;
	private List<Weight> weights;

	public SimpleWeightManager() {
		super();
		weights = new ArrayList<Weight>();
		coefficients = new ArrayList<Double>();
	}

	public SimpleWeightManager(List<Weight> weights, List<Double> coefficients, double threshold) {
		super();
		this.weights = weights;
		this.coefficients = coefficients;
		this.threshold = threshold;
	}

	public boolean add(Weight e) {
		return weights.add(e);
	}

	@Override
	public void assignWeights(CleverGraph graph, Map<Integer, String> uniProtIds) {
		for (int a : graph.getVertices()) {
			for (int b : graph.getVertices()) {
				if (a == b) continue;
				double score = 0;
				for (int i = 0; i < weights.size(); i++) {
					String sa = uniProtIds.get(a);
					String sb = uniProtIds.get(b);
					try {
						weights.get(i).setIds(sa, sb);
						score += coefficients.get(i) * weights.get(i).call();
					} catch (Exception e) {
						// totally okay; just don't add
					}
				}
				if (score >= threshold) {
					Collection<Integer> vertices = Arrays.asList(a, b);
					HomologyEdge edge = new HomologyEdge(score);
					graph.addHomologies(edge, vertices);
				}
			}
		}
	}

	public void setThreshold(double threshold) {
		this.threshold = threshold;
	}

	public void setWeights(List<Weight> weights) {
		this.weights = weights;
	}

}
