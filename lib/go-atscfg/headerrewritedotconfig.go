package atscfg

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import (
	"errors"
	"math"
	"net/url"
	"regexp"
	"strconv"
	"strings"

	"github.com/apache/trafficcontrol/lib/go-tc"
)

const HeaderRewritePrefix = "hdr_rw_"
const HeaderRewriteMidPrefix = "hdr_rw_mid_"
const ContentTypeHeaderRewriteDotConfig = ContentTypeTextASCII
const LineCommentHeaderRewriteDotConfig = LineCommentHash

// ServiceCategoryHeader is the internal service category header for logging the service category.
// Note this is internal, and will never be set in an HTTP Request or Response by ATS.
const ServiceCategoryHeader = "@CDN-SVC"

const MaxOriginConnectionsNoMax = 0 // 0 indicates no limit on origin connections

const HeaderRewriteFirstPrefix = HeaderRewritePrefix + "first_"
const HeaderRewriteInnerPrefix = HeaderRewritePrefix + "inner_"
const HeaderRewriteLastPrefix = HeaderRewritePrefix + "last_"

func FirstHeaderRewriteConfigFileName(dsName string) string {
	return HeaderRewriteFirstPrefix + dsName + ConfigSuffix
}

func InnerHeaderRewriteConfigFileName(dsName string) string {
	return HeaderRewriteInnerPrefix + dsName + ConfigSuffix
}

func LastHeaderRewriteConfigFileName(dsName string) string {
	return HeaderRewriteLastPrefix + dsName + ConfigSuffix
}

// HeaderRewriteDotConfigOpts contains settings to configure generation options.
type HeaderRewriteDotConfigOpts struct {
	// HdrComment is the header comment to include at the beginning of the file.
	// This should be the text desired, without comment syntax (like # or //). The file's comment syntax will be added.
	// To omit the header comment, pass the empty string.
	HdrComment string
}

// MakeHeaderRewriteDotConfig makes the header rewrite file for
// an Edge hdr_rw_ or Mid hdr_rw_mid_ or Topology hdr_rw_{first,inner,last} file,
// as generated by MakeMetaConfigFilesList.
func MakeHeaderRewriteDotConfig(
	fileName string,
	deliveryServices []DeliveryService,
	deliveryServiceServers []DeliveryServiceServer,
	server *Server,
	servers []Server,
	cacheGroupsArr []tc.CacheGroupNullable,
	tcServerParams []tc.Parameter,
	serverCapabilities map[int]map[ServerCapability]struct{},
	requiredCapabilities map[int]map[ServerCapability]struct{},
	topologiesArr []tc.Topology,
	opt *HeaderRewriteDotConfigOpts,
) (Cfg, error) {
	if opt == nil {
		opt = &HeaderRewriteDotConfigOpts{}
	}
	warnings := []string{}
	if server.CDNName == nil {
		return Cfg{}, makeErr(warnings, "this server missing CDNName")
	} else if tc.CacheType(server.Type) == tc.CacheTypeInvalid {
		return Cfg{}, makeErr(warnings, "this server missing Type")
	} else if server.HostName == nil {
		return Cfg{}, makeErr(warnings, "server missing HostName")
	} else if server.Cachegroup == nil {
		return Cfg{}, makeErr(warnings, "server missing Cachegroup")
	}

	cacheGroups, err := makeCGMap(cacheGroupsArr)
	if err != nil {
		return Cfg{}, makeErr(warnings, "making CacheGroup map: "+err.Error())
	}
	topologies := makeTopologyNameMap(topologiesArr)

	// TODO verify prefix and suffix? Perl doesn't
	dsName := fileName
	dsName = strings.TrimSuffix(dsName, ConfigSuffix)
	dsName = strings.TrimPrefix(dsName, HeaderRewriteFirstPrefix)
	dsName = strings.TrimPrefix(dsName, HeaderRewriteInnerPrefix)
	dsName = strings.TrimPrefix(dsName, HeaderRewriteLastPrefix)
	dsName = strings.TrimPrefix(dsName, HeaderRewriteMidPrefix)
	dsName = strings.TrimPrefix(dsName, HeaderRewritePrefix)

	ds := &DeliveryService{}
	for _, ids := range deliveryServices {
		if ids.XMLID == nil {
			warnings = append(warnings, "deliveryServices had DS with nil xmlId (name)")
			continue
		}
		if *ids.XMLID != dsName {
			continue
		}
		ds = &ids
		break
	}
	if ds.ID == nil {
		return Cfg{}, makeErr(warnings, "ds '"+dsName+"' not found")
	} else if ds.CDNName == nil {
		return Cfg{}, makeErr(warnings, "ds '"+dsName+"' missing cdn")
	}

	if ds.Topology != nil && *ds.Topology != "" && headerRewriteTopologyTier(fileName) == TopologyCacheTierInvalid {
		// write a blank file, rather than an error. Because this usually means a bad location parameter,
		// we don't want to break all of config generation during the migration to Topologies
		warnings = append(warnings, "header rewrite file '"+fileName+"' for non-topology, but delivery service has a Topology. Do you have a location Parameter that needs deleted? Writing blank file!")
		return Cfg{
			Text:        "",
			ContentType: ContentTypeHeaderRewriteDotConfig,
			LineComment: LineCommentHeaderRewriteDotConfig,
			Warnings:    warnings,
		}, nil
	}

	topology := tc.Topology{}
	if ds.Topology != nil && *ds.Topology != "" {
		topology = topologies[TopologyName(*ds.Topology)]
		if topology.Name == "" {
			return Cfg{}, makeErr(warnings, "DS "+*ds.XMLID+" topology '"+*ds.Topology+"' not found in Topologies!")
		}
	}

	atsRqstMaxHdrSize, paramWarns := getMaxRequestHeaderParam(tcServerParams)
	warnings = append(warnings, paramWarns...)

	atsMajorVersion, verWarns := getATSMajorVersion(tcServerParams)
	warnings = append(warnings, verWarns...)

	assignedTierPeers, assignWarns := getAssignedTierPeers(server, ds, servers, deliveryServiceServers, cacheGroupsArr, serverCapabilities, requiredCapabilities[*ds.ID])
	warnings = append(warnings, assignWarns...)

	dsOnlinePeerCount := 0
	for _, sv := range assignedTierPeers {
		if sv.Status == nil {
			warnings = append(warnings, "got server with nil status! skipping!")
			continue
		}
		if tc.CacheStatus(*sv.Status) == tc.CacheStatusReported || tc.CacheStatus(*sv.Status) == tc.CacheStatusOnline {
			dsOnlinePeerCount++
		}
	}
	numLastTierServers := dsOnlinePeerCount

	serverIsLastTier, err := headerRewriteServerIsLastTier(server, ds, fileName, cacheGroups, topology)
	if err != nil {
		return Cfg{}, makeErr(warnings, "getting header rewrite tier from delivery service: "+err.Error())
	}

	headerRewriteTxt, err := getTierHeaderRewrite(server, ds, fileName)
	if err != nil {
		return Cfg{}, makeErr(warnings, "getting header rewrite text from delivery service: "+err.Error())
	}

	text := makeHdrComment(opt.HdrComment)

	// Add the TC directives (which may be empty).
	// NOTE!! Custom TC injections MUST NOT EVER have a `[L]`. Doing so will break custom header rewrites!
	// NOTE!! The TC injections MUST be come before custom rewrites (EdgeHeaderRewrite, InnerHeaderRewrite, etc).
	//        If they're placed after, custom rewrites with [L] directives will result in them being applied inconsistently and incorrectly.
	text += makeATCHeaderRewriteDirectives(ds, headerRewriteTxt, serverIsLastTier, numLastTierServers, atsMajorVersion, atsRqstMaxHdrSize)

	if headerRewriteTxt != nil && *headerRewriteTxt != "" {
		hdrRw := returnRe.ReplaceAllString(*headerRewriteTxt, "\n")
		hdrRw = strings.TrimSpace(hdrRw)
		text += `
` + hdrRw + `
`
	}

	return Cfg{
		Text:        text,
		ContentType: ContentTypeHeaderRewriteDotConfig,
		LineComment: LineCommentHeaderRewriteDotConfig,
		Warnings:    warnings,
	}, nil
}

// headerRewriteServerIsLastTier is whether the server is the last tier for the delivery service of this header rewrite.
// This should NOT be abstracted into a function that could be used by any other config.
// This is whether the server is the last tier for this header rewrite. Which may not be true for other rewrites or configs.
func headerRewriteServerIsLastTier(server *Server, ds *DeliveryService, fileName string, cacheGroups map[tc.CacheGroupName]tc.CacheGroupNullable, topology tc.Topology) (bool, error) {
	if ds.Topology != nil {
		return headerRewriteTopologyTier(fileName) == TopologyCacheTierLast, nil
		// serverPlacement, err := getTopologyPlacement(tc.CacheGroupName(*server.Cachegroup), topology, cacheGroups, ds)
		// fmt.Printf("DEBUG ds '%v' topo placement %+v\n", *ds.XMLID, serverPlacement)
		// if err != nil {
		// 	return false, errors.New("getting topology placement: " + err.Error())
		// }
		// if !serverPlacement.InTopology {
		// 	return false, errors.New("server not in topology")
		// }
		// return serverPlacement.IsLastCacheTier, nil
	}

	serverIsMid := serverIsMid(server)
	dsUsesMids := ds.Type.UsesMidCache()
	dssIsLastTier := (!serverIsMid && !dsUsesMids) || (serverIsMid && dsUsesMids)
	return dssIsLastTier, nil

}

// headerRewriteTopologyTier returns the topology tier of this header rewrite file,
// or TopologyCacheTierInvalid if the file is not for a topology header rewrite.
func headerRewriteTopologyTier(fileName string) TopologyCacheTier {
	switch {
	case strings.HasPrefix(fileName, HeaderRewriteFirstPrefix):
		return TopologyCacheTierFirst
	case strings.HasPrefix(fileName, HeaderRewriteInnerPrefix):
		return TopologyCacheTierInner
	case strings.HasPrefix(fileName, HeaderRewriteLastPrefix):
		return TopologyCacheTierLast
	default:
		return TopologyCacheTierInvalid
	}
}

// getTierHeaderRewrite returns the ds MidHeaderRewrite if server is a MID, else the EdgeHeaderRewrite.
// Does not consider Topologies.
// May return nil, if the tier's HeaderRewrite (Edge or Mid) is nil.
func getTierHeaderRewrite(server *Server, ds *DeliveryService, fileName string) (*string, error) {
	if ds.Topology != nil {
		return getTierHeaderRewriteTopology(server, ds, fileName)
	}
	if serverIsMid(server) {
		return ds.MidHeaderRewrite, nil
	}
	return ds.EdgeHeaderRewrite, nil
}

func getTierHeaderRewriteTopology(server *Server, ds *DeliveryService, fileName string) (*string, error) {
	tier := headerRewriteTopologyTier(fileName)
	switch tier {
	case TopologyCacheTierFirst:
		return ds.FirstHeaderRewrite, nil
	case TopologyCacheTierInner:
		return ds.InnerHeaderRewrite, nil
	case TopologyCacheTierLast:
		return ds.LastHeaderRewrite, nil
	default:
		return nil, errors.New("Topology Header Rewrite called for DS '" + *ds.XMLID + "' on server '" + *server.HostName + "' file '" + fileName + "' had unknown topology cache tier '" + string(tier) + "'!")
	}
}

// serverIsMid returns true if server's type is Mid. The server.Type MUST NOT be nil. Does not consider Topologies.
func serverIsMid(server *Server) bool {
	return strings.HasPrefix(server.Type, tc.MidTypePrefix)
}

// getAssignedTierPeers returns all edges assigned to the DS if server is an edge,
// or all mids if server is a mid,
// or all the servers at the same tier, if the Delivery Service uses Topologies.
// Note this returns all servers of any status, not just ONLINE or REPORTED servers.
// Returns the list of assigned peers, and any warnings.
func getAssignedTierPeers(
	server *Server,
	ds *DeliveryService,
	servers []Server,
	deliveryServiceServers []DeliveryServiceServer,
	cacheGroups []tc.CacheGroupNullable,
	serverCapabilities map[int]map[ServerCapability]struct{},
	dsRequiredCapabilities map[ServerCapability]struct{},
) ([]Server, []string) {
	if ds.Topology != nil {
		return getTopologyTierServers(dsRequiredCapabilities, tc.CacheGroupName(*server.Cachegroup), servers, serverCapabilities)
	}
	if serverIsMid(server) {
		return getAssignedMids(server, ds, servers, deliveryServiceServers, cacheGroups)
	}
	return getAssignedEdges(ds, servers, deliveryServiceServers)
}

// getAssignedEdges returns all EDGE caches assigned to ds via DeliveryService-Service. Does not consider Topologies.
// Note this returns all servers of any status, not just ONLINE or REPORTED servers.
// Returns the list of assigned servers, and any warnings.
func getAssignedEdges(
	ds *DeliveryService,
	servers []Server,
	deliveryServiceServers []DeliveryServiceServer,
) ([]Server, []string) {
	warnings := []string{}

	dsServers := filterDSS(deliveryServiceServers, map[int]struct{}{*ds.ID: {}}, nil)

	dsServerIDs := map[int]struct{}{}
	for _, dss := range dsServers {
		if dss.DeliveryService != *ds.ID {
			continue
		}
		dsServerIDs[dss.Server] = struct{}{}
	}

	assignedEdges := []Server{}
	for _, server := range servers {
		if server.CDNName == nil {
			warnings = append(warnings, "servers had server with missing cdnName, skipping!")
			continue
		}
		if server.ID == nil {
			warnings = append(warnings, "servers had server with missing id, skipping!")
			continue
		}
		if *server.CDNName != *ds.CDNName {
			continue
		}
		if _, ok := dsServerIDs[*server.ID]; !ok && ds.Topology == nil {
			continue
		}
		assignedEdges = append(assignedEdges, server)
	}
	return assignedEdges, warnings
}

// getAssignedMids returns all MID caches with a child EDGE assigned to ds via DeliveryService-Service. Does not consider Topologies.
// Note this returns all servers of any status, not just ONLINE or REPORTED servers.
// Returns the list of assigned servers, and any warnings.
func getAssignedMids(
	server *Server,
	ds *DeliveryService,
	servers []Server,
	deliveryServiceServers []DeliveryServiceServer,
	cacheGroups []tc.CacheGroupNullable,
) ([]Server, []string) {
	warnings := []string{}
	assignedServers := map[int]struct{}{}
	for _, dss := range deliveryServiceServers {
		if dss.DeliveryService != *ds.ID {
			continue
		}
		assignedServers[dss.Server] = struct{}{}
	}

	serverCGs := map[tc.CacheGroupName]struct{}{}
	for _, sv := range servers {
		if sv.CDNName == nil {
			warnings = append(warnings, "TO returned Servers server with missing CDNName, skipping!")
			continue
		} else if sv.ID == nil {
			warnings = append(warnings, "TO returned Servers server with missing ID, skipping!")
			continue
		} else if sv.Status == nil {
			warnings = append(warnings, "TO returned Servers server with missing Status, skipping!")
			continue
		} else if sv.Cachegroup == nil {
			warnings = append(warnings, "TO returned Servers server with missing Cachegroup, skipping!")
			continue
		}

		if *sv.CDNName != *server.CDNName {
			continue
		}
		if _, ok := assignedServers[*sv.ID]; !ok && (ds.Topology == nil || *ds.Topology == "") {
			continue
		}
		if tc.CacheStatus(*sv.Status) != tc.CacheStatusReported && tc.CacheStatus(*sv.Status) != tc.CacheStatusOnline {
			continue
		}
		serverCGs[tc.CacheGroupName(*sv.Cachegroup)] = struct{}{}
	}

	parentCGs := map[string]struct{}{} // names of cachegroups which are parent cachegroups of the cachegroup of any edge assigned to the given DS
	for _, cg := range cacheGroups {
		if cg.Name == nil {
			warnings = append(warnings, "cachegroups had cachegroup with nil name, skipping!")
			continue
		}
		if cg.ParentName == nil {
			continue // this is normal for top-level cachegroups
		}
		if _, ok := serverCGs[tc.CacheGroupName(*cg.Name)]; !ok {
			continue
		}
		parentCGs[*cg.ParentName] = struct{}{}
	}

	assignedMids := []Server{}
	for _, server := range servers {
		if server.CDNName == nil {
			warnings = append(warnings, "TO returned Servers server with missing CDNName, skipping!")
			continue
		}
		if server.Cachegroup == nil {
			warnings = append(warnings, "TO returned Servers server with missing Cachegroup, skipping!")
			continue
		}
		if *server.CDNName != *ds.CDNName {
			continue
		}
		if _, ok := parentCGs[*server.Cachegroup]; !ok {
			continue
		}
		assignedMids = append(assignedMids, server)
	}

	return assignedMids, warnings
}

// getTopologyDSServerCount returns the servers in cg which will be used to serve ds.
// This should only be used for DSes with Topologies.
// It returns all servers in CG with the Capabilities of ds in cg.
// It will not be the number of servers for Delivery Services not using Topologies, which use DeliveryService-Server assignments instead.
// Returns the servers, and any warnings.
func getTopologyTierServers(dsRequiredCapabilities map[ServerCapability]struct{}, cg tc.CacheGroupName, servers []Server, serverCapabilities map[int]map[ServerCapability]struct{}) ([]Server, []string) {
	warnings := []string{}
	topoServers := []Server{}
	for _, sv := range servers {
		if sv.Cachegroup == nil {
			warnings = append(warnings, "Servers had server with nil cachegroup, skipping!")
			continue
		} else if sv.ID == nil {
			warnings = append(warnings, "Servers had server with nil id, skipping!")
			continue
		}

		if *sv.Cachegroup != string(cg) {
			continue
		}
		if !hasRequiredCapabilities(serverCapabilities[*sv.ID], dsRequiredCapabilities) {
			continue
		}
		topoServers = append(topoServers, sv)
	}
	return topoServers, warnings
}

var returnRe = regexp.MustCompile(`\s*__RETURN__\s*`)

// makeATCHeaderRewriteDirectives returns the Header Rewrite text for all per-Delivery-Service Traffic Control directives, such as MaxOriginConnections and ServiceCategory.
// These should be prepended to any custom Header Rewrites, in order to prevent [L] directive errors.
// The returned text may be empty, if no directives are configured.
//
// NOTE!! Custom TC injections MUST NOT ever have a `[L]`. Doing so will break custom header rewrites!
// NOTE!! The TC injections MUST be come before custom rewrites (EdgeHeaderRewrite, InnerHeaderRewrite, etc).
//        If they're placed after, custom rewrites with [L] directives will result in them being applied inconsistently and incorrectly.
//
// The headerRewriteTxt is the custom header rewrite from the Delivery Service. This should be used for any logic that depends on it. The various header rewrite fields (EdgeHeaderRewrite, InnerHeaderRewrite, etc should never be used inside this function, since this function doesn't know what tier the server is at. This function should not insert the headerRewriteText, but may use it to make decisions about what to insert.
func makeATCHeaderRewriteDirectives(ds *DeliveryService, headerRewriteTxt *string, serverIsLastTier bool, numLastTierServers int, atsMajorVersion int, atsRqstMaxHdrSize int) string {
	return makeATCHeaderRewriteDirectiveMaxOriginConns(ds, headerRewriteTxt, serverIsLastTier, numLastTierServers, atsMajorVersion) +
		makeATCHeaderRewriteDirectiveServiceCategoryHdr(ds, headerRewriteTxt) + makeATCHeaderRewriteDirectiveMaxRequestHeaderSize(ds, serverIsLastTier, atsRqstMaxHdrSize)
}

// makeATCHeaderRewriteDirectiveMaxOriginConns generates the Max Origin Connections header rewrite text, which may be empty.
func makeATCHeaderRewriteDirectiveMaxOriginConns(ds *DeliveryService, headerRewriteTxt *string, serverIsLastTier bool, numLastTierServers int, atsMajorVersion int) string {
	if !serverIsLastTier ||
		(ds.MaxOriginConnections == nil || *ds.MaxOriginConnections < 1) ||
		numLastTierServers < 1 {
		return ""
	}

	maxOriginConnectionsPerServer := int(math.Round(float64(*ds.MaxOriginConnections) / float64(numLastTierServers)))
	if maxOriginConnectionsPerServer < 1 {
		maxOriginConnectionsPerServer = 1
	}

	if atsMajorVersion < 9 {
		return `
cond %{REMAP_PSEUDO_HOOK}
set-config proxy.config.http.origin_max_connections ` + strconv.Itoa(maxOriginConnectionsPerServer) + `
`
	}

	// if the DS doesn't specify a match, use host. This will make ATS treat different hostnames (but not IPs) as different, for max origin connections.
	// Which is what we want. It's common for different DSes to CNAME the same origin, such as a cloud provider.
	// In that case, we want to give each hostname=remap=deliveryservice its own max
	maybeMatch := ""
	if headerRewriteTxt == nil || !strings.Contains(*headerRewriteTxt, `proxy.config.http.per_server.connection.match`) {
		maybeMatch += `set-config proxy.config.http.per_server.connection.match host
`
	}
	return `
cond %{REMAP_PSEUDO_HOOK}
` + maybeMatch + `set-config proxy.config.http.per_server.connection.max ` + strconv.Itoa(maxOriginConnectionsPerServer) + `
`
}

func makeATCHeaderRewriteDirectiveServiceCategoryHdr(ds *DeliveryService, headerRewriteTxt *string) string {
	if (ds.ServiceCategory == nil || *ds.ServiceCategory == "") ||
		(headerRewriteTxt != nil && strings.Contains(*headerRewriteTxt, ServiceCategoryHeader)) { // if the custom header rewrite already contains the service category header, don't add another one
		return ""
	}
	// Escape the ServiceCategory, which is user input, to prevent exploits. No Delivery Service should be able to break the CDN.
	// This is more conservative than necessary, but Go doesn't have a HeaderEscape, and valid path characters are a subset of valid header values.
	escapedServiceCategory := url.PathEscape(*ds.ServiceCategory)
	return `
cond %{REMAP_PSEUDO_HOOK}
set-header ` + ServiceCategoryHeader + ` "` + *ds.XMLID + `|` + escapedServiceCategory + `"
`
}

func makeATCHeaderRewriteDirectiveMaxRequestHeaderSize(ds *DeliveryService, serverIsLastTier bool, atsRqstMaxHdrSize int) string {
	if serverIsLastTier || ds.MaxRequestHeaderBytes == nil || *ds.MaxRequestHeaderBytes < 1 {
		return ""
	}
	hdrTxt := "cond %{REMAP_PSEUDO_HOOK}\ncond % cqhl > " + strconv.Itoa(*ds.MaxRequestHeaderBytes) + "\nset-status 400"
	warnTxt := "#TO Max Request Header Size: " + strconv.Itoa(*ds.MaxRequestHeaderBytes) +
		",is larger than or equal to the global setting of " + strconv.Itoa(atsRqstMaxHdrSize) + ", header rw will be ignored.\n"
	if *ds.MaxRequestHeaderBytes >= atsRqstMaxHdrSize {
		return warnTxt + hdrTxt
	} else {
		return hdrTxt
	}
}
