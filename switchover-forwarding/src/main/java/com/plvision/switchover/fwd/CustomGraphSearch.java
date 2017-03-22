/*
 * Copyright 2016-present PLVision
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * PLVision, developers@plvision.eu
 */

package com.plvision.switchover.fwd;

import java.util.Iterator;
import org.onlab.graph.DijkstraGraphSearch;
import org.onlab.graph.Edge;
import org.onlab.graph.EdgeWeight;
import org.onlab.graph.Graph;
import org.onlab.graph.MutableAdjacencyListsGraph;
import org.onlab.graph.MutableGraph;
import org.onlab.graph.Vertex;


public class CustomGraphSearch<V extends Vertex, E extends Edge<V>> extends DijkstraGraphSearch<V, E> {
	@SuppressWarnings("unchecked")
    public Result<V, E> search(Graph<V, E> graph, V src, V dst, EdgeWeight<V, E> weight, int maxPaths, int depth) {
		
		final EdgeWeight<V, E> weightf = weight;
		MutableGraph<V, Edge<V>> gt = mutableCopy(graph);
		DefaultResult tmpresult;
		DefaultResult result = new DefaultResult(src, dst);
		do {
	        tmpresult = (DefaultResult) super.search((Graph<V, E>) gt, src, dst, weightf, maxPaths);
			if (tmpresult.paths().isEmpty()) break;
	        for (org.onlab.graph.Path<V, E> path : tmpresult.paths()) {
	        	Iterator<E> iterator = path.edges().iterator();
	        	@SuppressWarnings("rawtypes")
				Edge e = iterator.next();
	            gt.removeEdge(e);
	            result.paths().add(path);
	        }
		} while (--depth >= 0);
		return result;
	}

	/**
     * Returns the current cost to reach the specified vertex.
     *
     * @param v vertex to reach
     * @return cost to reach the vertex
     */
    @SuppressWarnings("unused")
	private double cost(V v, DefaultResult res) {
        Double c = res.costs().get(v);
        return c == null ? Double.MAX_VALUE : c;
    }

    
    private Class<?> clazzV;

    public Class<?> classV() {
        return clazzV;
    }

    private Class<?> clazzE;

    public Class<?> classE() {
        return clazzE;
    }

    /**
     * Creates a mutable copy of an immutable graph.
     *
     * @param graph   immutable graph
     * @return mutable copy
     */
    @SuppressWarnings("rawtypes")
	public MutableGraph mutableCopy(Graph<V, E> graph) {
        clazzV = graph.getVertexes().iterator().next().getClass();
        clazzE = graph.getEdges().iterator().next().getClass();
        return new MutableAdjacencyListsGraph<V, E>(graph.getVertexes(), graph.getEdges());
    }

}
