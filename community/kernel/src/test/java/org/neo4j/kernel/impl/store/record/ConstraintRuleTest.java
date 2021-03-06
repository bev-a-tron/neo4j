/*
 * Copyright (c) 2002-2017 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.kernel.impl.store.record;

import org.junit.Test;

import org.neo4j.kernel.api.schema_new.constaints.ConstraintDescriptor;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.neo4j.kernel.api.schema_new.constaints.ConstraintDescriptorFactory.existsForLabel;
import static org.neo4j.kernel.api.schema_new.constaints.ConstraintDescriptorFactory.existsForRelType;
import static org.neo4j.kernel.api.schema_new.constaints.ConstraintDescriptorFactory.uniqueForLabel;
import static org.neo4j.kernel.api.schema_new.constaints.ConstraintDescriptorFactory.uniqueForRelType;
import static org.neo4j.test.assertion.Assert.assertException;

public class ConstraintRuleTest extends SchemaRuleTestBase
{
    @Test
    public void shouldCreateUniquenessConstraint() throws Exception
    {
        // GIVEN
        ConstraintDescriptor descriptor = uniqueForLabel( LABEL_ID, PROPERTY_ID_1 );
        ConstraintRule constraintRule = ConstraintRule.constraintRule( RULE_ID, descriptor );

        // THEN
        assertThat( constraintRule.getId(), equalTo( RULE_ID ) );
        assertThat( constraintRule.schema(), equalTo( descriptor.schema() ) );
        assertThat( constraintRule.getConstraintDescriptor(), equalTo( descriptor ) );
        assertException( constraintRule::getOwnedIndex, IllegalStateException.class, "" );
    }

    @Test
    public void shouldCreateUniquenessConstraintWithOwnedIndex() throws Exception
    {
        // GIVEN
        ConstraintDescriptor descriptor = uniqueForLabel( LABEL_ID, PROPERTY_ID_1 );
        ConstraintRule constraintRule = ConstraintRule.constraintRule( RULE_ID, descriptor, RULE_ID_2 );

        // THEN
        assertThat( constraintRule.getConstraintDescriptor(), equalTo( descriptor ) );
        assertThat( constraintRule.getOwnedIndex(), equalTo( RULE_ID_2 ) );
    }

    @Test
    public void shouldCreateExistenceConstraint() throws Exception
    {
        // GIVEN
        ConstraintDescriptor descriptor = existsForLabel( LABEL_ID, PROPERTY_ID_1 );
        ConstraintRule constraintRule = ConstraintRule.constraintRule( RULE_ID, descriptor );

        // THEN
        assertThat( constraintRule.getId(), equalTo( RULE_ID ) );
        assertThat( constraintRule.schema(), equalTo( descriptor.schema() ) );
        assertThat( constraintRule.getConstraintDescriptor(), equalTo( descriptor ) );
        assertException( constraintRule::getOwnedIndex, IllegalStateException.class, "" );
    }

    @Test
    public void indexRulesAreEqualBasedOnConstraintDescriptor() throws Exception
    {
        assertEqualityByDescriptor( existsForLabel( LABEL_ID, PROPERTY_ID_1 ) );
        assertEqualityByDescriptor( uniqueForLabel( LABEL_ID, PROPERTY_ID_1 ) );
        assertEqualityByDescriptor( existsForRelType( REL_TYPE_ID, PROPERTY_ID_1 ) );
        assertEqualityByDescriptor( uniqueForRelType( REL_TYPE_ID, PROPERTY_ID_1 ) );
        assertEqualityByDescriptor( existsForLabel( LABEL_ID, PROPERTY_ID_1, PROPERTY_ID_2 ) );
        assertEqualityByDescriptor( uniqueForLabel( LABEL_ID, PROPERTY_ID_1, PROPERTY_ID_2 ) );
    }

    private void assertEqualityByDescriptor( ConstraintDescriptor descriptor )
    {
        ConstraintRule rule1 = ConstraintRule.constraintRule( RULE_ID, descriptor, RULE_ID_2 );
        ConstraintRule rule2 = ConstraintRule.constraintRule( RULE_ID_2, descriptor );

        assertEquality( rule1, rule2 );
    }
}
