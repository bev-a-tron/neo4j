/*
 * Copyright (c) 2002-2017 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.causalclustering.load_balancing.plugins.server_policies;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import org.neo4j.helpers.AdvertisedSocketAddress;

import static org.junit.Assert.assertEquals;
import static org.neo4j.helpers.collection.Iterators.asSet;

public class AnyTagFilterTest
{
    @Test
    public void shouldReturnServersMatchingAnyTag() throws Exception
    {
        // given
        AnyTagFilter tagFilter = new AnyTagFilter( asSet( "china-west", "europe" ) );

        ServerInfo serverA = new ServerInfo( new AdvertisedSocketAddress( "bolt", 1 ), asSet( "china-west" ) );
        ServerInfo serverB = new ServerInfo( new AdvertisedSocketAddress( "bolt", 2 ), asSet( "europe" ) );
        ServerInfo serverC = new ServerInfo( new AdvertisedSocketAddress( "bolt", 3 ), asSet( "china", "china-west" ) );
        ServerInfo serverD = new ServerInfo( new AdvertisedSocketAddress( "bolt", 4 ), asSet( "china-west", "china" ) );
        ServerInfo serverE = new ServerInfo( new AdvertisedSocketAddress( "bolt", 5 ), asSet( "china-east", "asia" ) );
        ServerInfo serverF = new ServerInfo( new AdvertisedSocketAddress( "bolt", 6 ), asSet( "europe-west" ) );
        ServerInfo serverG = new ServerInfo( new AdvertisedSocketAddress( "bolt", 7 ), asSet( "china-west", "europe" ) );
        ServerInfo serverH = new ServerInfo( new AdvertisedSocketAddress( "bolt", 8 ), asSet( "africa" ) );

        Set<ServerInfo> data = asSet( serverA, serverB, serverC, serverD, serverE, serverF, serverG, serverH );

        // when
        Set<ServerInfo> output = tagFilter.apply( data );

        // then
        Set<Integer> ports = new HashSet<>();
        for ( ServerInfo info : output )
        {
            ports.add( info.boltAddress().getPort() );
        }

        assertEquals( asSet( 1, 2, 3, 4, 7 ), ports );
    }
}
