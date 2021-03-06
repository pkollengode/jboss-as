/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.test.integration.ejb.entity.cmp.callback;

import static org.junit.Assert.fail;

import javax.ejb.FinderException;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.as.test.integration.ejb.entity.cmp.AbstractCmpTest;
import org.jboss.as.test.integration.ejb.entity.cmp.CmpTestRunner;
import org.jboss.logging.Logger;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Check whether ejbCreate ejbPostCreate and ejbStore is called during creating an entity bean.
 * Check if the findByPrimaryKey is optimized to not call DB sync by check whether the ejbStore is called if the finder is executed.
 * The creation and the test will be called in different transactions to ensure that the entity bean was passivated and is taken
 * out of the instance pool to check that the pooled instance is correct reseted for reuse.
 *
 * @author <a href="mailto:wfink@redhat.com">Wolf-Dieter Fink</a>
 */
@RunWith(CmpTestRunner.class)
public class CheckEjbStoreDifferentTxUnitTestCase extends AbstractCmpTest {
    private static Logger log = Logger.getLogger(CheckEjbStoreDifferentTxUnitTestCase.class);

    private static final String ARCHIVE_NAME = "cmp2-findByPK.jar";
    private static final String ENTITY_LOOKUP = "java:module/SimpleEntity!"+SimpleEntityLocalHome.class.getName();

    @Deployment
    public static Archive<?> deploy() {
        JavaArchive jar = ShrinkWrap.create(JavaArchive.class, ARCHIVE_NAME);
        jar.addPackage(CheckEjbStoreDifferentTxUnitTestCase.class.getPackage());
        jar.addAsManifestResource(CheckEjbStoreDifferentTxUnitTestCase.class.getPackage(), "ejb-jar.xml", "ejb-jar.xml");
        jar.addAsManifestResource(CheckEjbStoreDifferentTxUnitTestCase.class.getPackage(), "jbosscmp-jdbc.xml", "jbosscmp-jdbc.xml");
        AbstractCmpTest.addDeploymentAssets(jar);
        return jar;
    }

    private SimpleEntityLocalHome getHome() {
        try {
            return (SimpleEntityLocalHome) iniCtx.lookup(ENTITY_LOOKUP);
        } catch (Exception e) {
            log.error("failed", e);
            fail("Exception in getHome: " + e.getMessage());
        }
        return null;
    }

    @Override
    public void setUpEjb() throws Exception {
        SimpleEntityLocalHome simpleHome = getHome();

        simpleHome.create(1l, "Entity #1");
        Assert.assertEquals("ejbCreate must be called for Entity#1", 1, TestResults.getNumberOfCalls("SimpleEntity#1.ejbCreate"));
        Assert.assertEquals("ejbPostCreate must be called for Entity#1", 1, TestResults.getNumberOfCalls("SimpleEntity#1.ejbPostCreate"));
        Assert.assertEquals("ejbStore must be called for Entity#1", 1, TestResults.getNumberOfCalls("SimpleEntity#1.ejbStore"));
        simpleHome.create(2l, "Entity #2");
        Assert.assertEquals("ejbCreate must be called for Entity#2", 1, TestResults.getNumberOfCalls("SimpleEntity#2.ejbCreate"));
        Assert.assertEquals("ejbPostCreate must be called for Entity#2", 1, TestResults.getNumberOfCalls("SimpleEntity#2.ejbPostCreate"));
        Assert.assertEquals("ejbStore must be called for Entity#2", 1, TestResults.getNumberOfCalls("SimpleEntity#2.ejbStore"));
        TestResults.resetAll();
    }


    /**
     * From the spec a call to a finder must flush all entities to the database to ensure
     * a correct result for the query.
     * This is not necessary for the 'findByPrimaryKey' method and will be a performance improvement.
     *
     * The test checks whether the entity is not flushed during calls of findByPrimaryKey if the entities are
     * created in a different Tx.
     *
     * @throws FinderException
     */
    @Test
    public void testDbSyncDifferentTx() throws FinderException {
        SimpleEntityLocalHome simpleHome = getHome();

        log.debug("read Enity #1");
        SimpleEntityLocal e1 = simpleHome.findByPrimaryKey(1l);
        Assert.assertFalse("ejbStore unexpected called after findByPrimaryKey!", TestResults.isCalled("SimpleEntity#1.ejbStore"));
        log.debug("read Enity #2");
        SimpleEntityLocal e2 = simpleHome.findByPrimaryKey(2l);

        log.debug("change Enity #1");
        e1.setName("Entity #1 changed");
        Assert.assertFalse("ejbStore called unexpected after change!", TestResults.isCalled("SimpleEntity#1.ejbStore"));

        log.debug("findByPKey Enity #2");
        e2 = simpleHome.findByPrimaryKey(2l);
        Assert.assertFalse("Entity #1 was unexpected flushed to the DB",TestResults.isCalled("SimpleEntity#1.ejbStore"));

        log.debug("findById Enity #2");
        e2 = simpleHome.findById(2l);
        Assert.assertEquals("Entity #1 was not flushed to the DB ", 1, TestResults.getNumberOfCalls("SimpleEntity#1.ejbStore"));
        log.debug("leaving test");
    }
}
