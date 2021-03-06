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
package org.neo4j.kernel.impl.api.integrationtest;

import org.junit.Test;

import java.util.Iterator;

import org.neo4j.SchemaHelper;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.kernel.api.ReadOperations;
import org.neo4j.kernel.api.SchemaWriteOperations;
import org.neo4j.kernel.api.Statement;
import org.neo4j.kernel.api.StatementTokenNameLookup;
import org.neo4j.kernel.api.TokenWriteOperations;
import org.neo4j.kernel.api.constraints.NodePropertyConstraint;
import org.neo4j.kernel.api.constraints.NodePropertyExistenceConstraint;
import org.neo4j.kernel.api.constraints.UniquenessConstraint;
import org.neo4j.kernel.api.exceptions.KernelException;
import org.neo4j.kernel.api.exceptions.TransactionFailureException;
import org.neo4j.kernel.api.exceptions.schema.ConstraintVerificationFailedKernelException;
import org.neo4j.kernel.api.exceptions.schema.CreateConstraintFailureException;
import org.neo4j.kernel.api.exceptions.schema.DropConstraintFailureException;
import org.neo4j.kernel.api.exceptions.schema.NoSuchConstraintException;
import org.neo4j.kernel.api.properties.Property;
import org.neo4j.kernel.api.schema.IndexDescriptor;
import org.neo4j.kernel.api.schema.IndexDescriptorFactory;
import org.neo4j.kernel.api.schema.NodePropertyDescriptor;
import org.neo4j.kernel.api.schema_new.SchemaDescriptorFactory;
import org.neo4j.kernel.api.schema_new.constaints.ConstraintDescriptorFactory;
import org.neo4j.kernel.api.security.AnonymousContext;
import org.neo4j.kernel.api.security.SecurityContext;
import org.neo4j.kernel.api.schema_new.index.NewIndexDescriptor;
import org.neo4j.kernel.api.schema_new.index.NewIndexDescriptorFactory;
import org.neo4j.kernel.impl.storageengine.impl.recordstorage.RecordStorageEngine;
import org.neo4j.kernel.impl.store.NeoStores;
import org.neo4j.kernel.impl.store.SchemaStorage;
import org.neo4j.kernel.impl.store.record.ConstraintRule;
import org.neo4j.kernel.impl.store.record.IndexRule;

import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.neo4j.graphdb.Label.label;
import static org.neo4j.helpers.collection.Iterators.asSet;
import static org.neo4j.helpers.collection.Iterators.emptySetOf;
import static org.neo4j.helpers.collection.Iterators.single;

public class UniquenessConstraintCreationIT
        extends AbstractConstraintCreationIT<UniquenessConstraint,NodePropertyDescriptor>
{
    private static final String DUPLICATED_VALUE = "apa";
    private NewIndexDescriptor uniqueIndex;

    @Override
    int initializeLabelOrRelType( TokenWriteOperations tokenWriteOperations, String name ) throws KernelException
    {
        return tokenWriteOperations.labelGetOrCreateForName( KEY );
    }

    @Override
    UniquenessConstraint createConstraint( SchemaWriteOperations writeOps, NodePropertyDescriptor descriptor ) throws Exception
    {
        return writeOps.uniquePropertyConstraintCreate( descriptor );
    }

    @Override
    void createConstraintInRunningTx( GraphDatabaseService db, String type, String property )
    {
        SchemaHelper.createUniquenessConstraint( db, type, property );
    }

    @Override
    UniquenessConstraint newConstraintObject( NodePropertyDescriptor descriptor )
    {
        return new UniquenessConstraint(descriptor);
    }

    @Override
    void dropConstraint( SchemaWriteOperations writeOps, UniquenessConstraint constraint ) throws Exception
    {
        writeOps.constraintDrop( constraint );
    }

    @Override
    void createOffendingDataInRunningTx( GraphDatabaseService db )
    {
        db.createNode( label( KEY ) ).setProperty( PROP, DUPLICATED_VALUE );
        db.createNode( label( KEY ) ).setProperty( PROP, DUPLICATED_VALUE );
    }

    @Override
    void removeOffendingDataInRunningTx( GraphDatabaseService db )
    {
        try ( ResourceIterator<Node> nodes = db.findNodes( label( KEY ), PROP, DUPLICATED_VALUE ) )
        {
            while ( nodes.hasNext() )
            {
                nodes.next().delete();
            }
        }
    }

    @Override
    NodePropertyDescriptor makeDescriptor( int typeId, int propertyKeyId )
    {
        uniqueIndex = NewIndexDescriptorFactory.uniqueForLabel( typeId, propertyKeyId );
        return new NodePropertyDescriptor( typeId, propertyKeyId );
    }

    @Test
    public void shouldAbortConstraintCreationWhenDuplicatesExist() throws Exception
    {
        // given
        Statement statement = statementInNewTransaction( AnonymousContext.writeToken() );
        // name is not unique for Foo in the existing data

        int foo = statement.tokenWriteOperations().labelGetOrCreateForName( "Foo" );
        int name = statement.tokenWriteOperations().propertyKeyGetOrCreateForName( "name" );

        long node1 = statement.dataWriteOperations().nodeCreate();

        statement.dataWriteOperations().nodeAddLabel( node1, foo );
        statement.dataWriteOperations().nodeSetProperty( node1, Property.stringProperty( name, "foo" ) );

        long node2 = statement.dataWriteOperations().nodeCreate();
        statement.dataWriteOperations().nodeAddLabel( node2, foo );

        statement.dataWriteOperations().nodeSetProperty( node2, Property.stringProperty( name, "foo" ) );
        commit();

        // when
        NodePropertyDescriptor descriptor1 = new NodePropertyDescriptor( foo, name );
        try
        {
            SchemaWriteOperations schemaWriteOperations = schemaWriteOperationsInNewTransaction();
            schemaWriteOperations.uniquePropertyConstraintCreate( descriptor1 );

            fail( "expected exception" );
        }
        // then
        catch ( CreateConstraintFailureException ex )
        {
            assertEquals( new UniquenessConstraint( descriptor1 ), ex.constraint() );
            Throwable cause = ex.getCause();
            assertThat( cause, instanceOf( ConstraintVerificationFailedKernelException.class ) );

            String expectedMessage =
                    String.format( "Multiple nodes with label `%s` have property `%s` = '%s':%n  node(%d)%n  node(%d)",
                            "Foo", "name", "foo", node1, node2 );
            String actualMessage = userMessage( (ConstraintVerificationFailedKernelException) cause );
            assertEquals( expectedMessage, actualMessage );
        }
    }

    @Test
    public void shouldCreateAnIndexToGoAlongWithAUniquePropertyConstraint() throws Exception
    {
        // when
        SchemaWriteOperations schemaWriteOperations = schemaWriteOperationsInNewTransaction();
        schemaWriteOperations.uniquePropertyConstraintCreate( descriptor );
        commit();

        // then
        ReadOperations readOperations = readOperationsInNewTransaction();
        assertEquals( asSet( uniqueIndex ), asSet( readOperations.uniqueIndexesGetAll() ) );
    }

    @Test
    public void shouldDropCreatedConstraintIndexWhenRollingBackConstraintCreation() throws Exception
    {
        // given
        Statement statement = statementInNewTransaction( SecurityContext.AUTH_DISABLED );
        statement.schemaWriteOperations().uniquePropertyConstraintCreate( descriptor );
        assertEquals( asSet( uniqueIndex ),
                asSet( statement.readOperations().uniqueIndexesGetAll() ) );

        // when
        rollback();

        // then
        ReadOperations readOperations = readOperationsInNewTransaction();
        assertEquals( emptySetOf( NewIndexDescriptor.class ), asSet( readOperations.uniqueIndexesGetAll() ) );
        commit();
    }

    @Test
    public void shouldNotDropUniquePropertyConstraintThatDoesNotExistWhenThereIsAPropertyExistenceConstraint()
            throws Exception
    {
        // given
        SchemaWriteOperations schemaWriteOperations = schemaWriteOperationsInNewTransaction();
        NodePropertyExistenceConstraint constraint =
                schemaWriteOperations.nodePropertyExistenceConstraintCreate( descriptor );
        commit();

        // when
        try
        {
            SchemaWriteOperations statement = schemaWriteOperationsInNewTransaction();
            statement.constraintDrop( new UniquenessConstraint( constraint.descriptor() ) );

            fail( "expected exception" );
        }
        // then
        catch ( DropConstraintFailureException e )
        {
            assertThat( e.getCause(), instanceOf( NoSuchConstraintException.class ) );
        }
        finally
        {
            rollback();
        }

        // then
        {
            ReadOperations statement = readOperationsInNewTransaction();

            Iterator<NodePropertyConstraint> constraints =
                    statement.constraintsGetForLabelAndPropertyKey( descriptor );

            assertEquals( constraint, single( constraints ) );
        }
    }

    @Test
    public void committedConstraintRuleShouldCrossReferenceTheCorrespondingIndexRule() throws Exception
    {
        // when
        SchemaWriteOperations statement = schemaWriteOperationsInNewTransaction();
        statement.uniquePropertyConstraintCreate( descriptor );
        commit();

        // then
        SchemaStorage schema = new SchemaStorage( neoStores().getSchemaStore() );
        IndexRule indexRule = schema.indexGetForSchema( SchemaDescriptorFactory.forLabel( typeId, propertyKeyId ) );
        ConstraintRule constraintRule = schema.constraintsGetSingle(
                ConstraintDescriptorFactory.uniqueForLabel( typeId, propertyKeyId ) );
        assertEquals( constraintRule.getId(), indexRule.getOwningConstraint().longValue() );
        assertEquals( indexRule.getId(), constraintRule.getOwnedIndex() );
    }

    private NeoStores neoStores()
    {
        return db.getDependencyResolver().resolveDependency( RecordStorageEngine.class ).testAccessNeoStores();
    }

    @Test
    public void shouldDropConstraintIndexWhenDroppingConstraint() throws Exception
    {
        // given
        Statement statement = statementInNewTransaction( SecurityContext.AUTH_DISABLED );
        UniquenessConstraint constraint =
                statement.schemaWriteOperations().uniquePropertyConstraintCreate( descriptor );
        assertEquals( asSet( uniqueIndex ),
                asSet( statement.readOperations().uniqueIndexesGetAll() ) );
        commit();

        // when
        SchemaWriteOperations schemaWriteOperations = schemaWriteOperationsInNewTransaction();
        schemaWriteOperations.constraintDrop( constraint );
        commit();

        // then
        ReadOperations readOperations = readOperationsInNewTransaction();
        assertEquals( emptySetOf( NewIndexDescriptor.class ), asSet( readOperations.uniqueIndexesGetAll() ) );
        commit();
    }

    private String userMessage( ConstraintVerificationFailedKernelException cause )
            throws TransactionFailureException
    {
        StatementTokenNameLookup lookup = new StatementTokenNameLookup( readOperationsInNewTransaction() );
        String actualMessage = cause.getUserMessage( lookup );
        commit();
        return actualMessage;
    }
}
