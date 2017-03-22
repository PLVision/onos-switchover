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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.onlab.graph.EdgeWeight;
import org.onlab.graph.GraphPathSearch;
import org.onlab.graph.GraphPathSearch.Result;
import org.onosproject.net.DefaultPath;
import org.onosproject.net.DeviceId;
import org.onosproject.net.DisjointPath;
import org.onosproject.net.Link;
import org.onosproject.net.Path;
import org.onosproject.net.provider.ProviderId;
import org.onosproject.net.topology.DefaultTopologyVertex;
import org.onosproject.net.topology.HopCountLinkWeight;
import org.onosproject.net.topology.TopologyEdge;
import org.onosproject.net.topology.TopologyGraph;
import org.onosproject.net.topology.TopologyService;
import org.onosproject.net.topology.TopologyVertex;

import com.google.common.collect.ImmutableSet;

/**
 * Path finder class
 */
public class PathFinder {

	private static final CustomGraphSearch<TopologyVertex, TopologyEdge> CUST = new CustomGraphSearch<>();

	/**
	 * Searching of primary and backup paths between two devices.
	 * 
	 * @param service - topology service
	 * @param src - source device Id
	 * @param dst - destination device Id
	 * 
	 * @return set of paths
	 */
	public static Set<Path> result(TopologyService service, DeviceId src, DeviceId dst) {
		ImmutableSet.Builder<Path> builder = ImmutableSet.builder();
		Set<Path> paths1 = service.getPaths(service.currentTopology(), src, dst);
		Set<DisjointPath> paths2 = service.getDisjointPaths(service.currentTopology(), src, dst);
		paths1.forEach(path -> {
			builder.add(path);
		});
		paths2.forEach(path -> {
			builder.add(path.backup());
		});
		return builder.build();
	}

//	private static final DijkstraGraphSearch<TopologyVertex, TopologyEdge> DIJKSTRA = new DijkstraGraphSearch<>();
//	private static final DepthFirstSearch<TopologyVertex, TopologyEdge> DEPF = new DepthFirstSearch<>();
//	private static final SuurballeGraphSearchExt<TopologyVertex, TopologyEdge> SURA = new SuurballeGraphSearchExt<>();
//	private static final DijkstraGraphSearchExt<TopologyVertex, TopologyEdge> TEST = new DijkstraGraphSearchExt<>();
//	private static final DepthFirstSearchExt<TopologyVertex, TopologyEdge> TEST = new DepthFirstSearchExt<>();


	/**
	 * Searching for all paths between two devices according to defined deep of search.
	 * 
	 * @param service - topology service
	 * @param src - source device Id
	 * @param dst - destination device Id
	 * @param deep - deep of search as path cost
	 * 
	 * @return set of paths
	 */
	public static Set<Path> result(TopologyService service, DeviceId src, DeviceId dst, int deep) {
		ImmutableSet.Builder<Path> builder = ImmutableSet.builder();
		TopologyGraph graph = service.getGraph(service.currentTopology());
		DefaultTopologyVertex vs = new DefaultTopologyVertex(src);
		DefaultTopologyVertex vd = new DefaultTopologyVertex(dst);
		@SuppressWarnings("rawtypes")
		EdgeWeight weight = new HopCountLinkWeight(10);
		
		
		@SuppressWarnings({ "rawtypes", "unchecked" })
		List<Path> list = new ArrayList();
		
		
		@SuppressWarnings("unchecked")
		Result<TopologyVertex, TopologyEdge> result = CUST.search(graph, vs, vd, weight, GraphPathSearch.ALL_PATHS, 4);
		for (org.onlab.graph.Path<TopologyVertex, TopologyEdge> path : result.paths()) {
			List<Link> links = path.edges().stream().map(TopologyEdge::link).collect(Collectors.toList());
			DefaultPath dp = new DefaultPath(ProviderId.NONE, links, path.cost());
			list.add(dp);
//			builder.add(dp);
		}
		
		Collections.sort(list, new Comparator<Path>() {
		    public int compare(Path p1, Path p2) {
		        Double c1 = p1.cost();
		        Double c2 = p2.cost();
		        return (c1 < c2 ? -1 : (c1 == c2 ? 0 : 1));
		    }
		});

		builder.addAll(list);

		return builder.build();
	}
}
