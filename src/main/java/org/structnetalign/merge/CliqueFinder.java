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

package org.structnetalign.merge;

import java.util.NavigableSet;
import java.util.Set;

import org.apache.commons.collections15.Transformer;

import edu.uci.ics.jung.graph.UndirectedGraph;

/**
 * A Transformation that can find maximal and maximum cliques in an undirected graph.
 * @author dmyersturnbull
 *
 * @param <V>
 * @param <E>
 */
public interface CliqueFinder<V,E> extends Transformer<UndirectedGraph<V,E>, NavigableSet<Set<V>>> {

	NavigableSet<Set<V>> getMaximumCliques(UndirectedGraph<V,E> graph);

	NavigableSet<Set<V>> getMaximalCliques(UndirectedGraph<V,E> graph);
	
}
