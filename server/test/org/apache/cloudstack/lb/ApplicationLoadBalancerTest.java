// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
// 
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
package org.apache.cloudstack.lb;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import junit.framework.TestCase;

import org.apache.cloudstack.network.lb.ApplicationLoadBalancerManagerImpl;
import org.apache.cloudstack.network.lb.ApplicationLoadBalancerRule;
import org.apache.cloudstack.network.lb.ApplicationLoadBalancerRuleVO;
import org.apache.cloudstack.network.lb.dao.ApplicationLoadBalancerRuleDao;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.cloud.event.dao.UsageEventDao;
import com.cloud.exception.InsufficientAddressCapacityException;
import com.cloud.exception.InsufficientVirtualNetworkCapcityException;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.NetworkRuleConflictException;
import com.cloud.exception.UnsupportedServiceException;
import com.cloud.network.Network;
import com.cloud.network.Network.Capability;
import com.cloud.network.Network.Service;
import com.cloud.network.NetworkModel;
import com.cloud.network.Networks.TrafficType;
import com.cloud.network.dao.FirewallRulesDao;
import com.cloud.network.dao.NetworkVO;
import com.cloud.network.lb.LoadBalancingRule;
import com.cloud.network.lb.LoadBalancingRulesManager;
import com.cloud.network.rules.FirewallRuleVO;
import com.cloud.network.rules.LoadBalancerContainer.Scheme;
import com.cloud.user.AccountManager;
import com.cloud.user.AccountVO;
import com.cloud.user.UserContext;
import com.cloud.user.UserVO;
import com.cloud.utils.component.ComponentContext;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.net.Ip;
import com.cloud.utils.net.NetUtils;

/**
 * This class is responsible for unittesting the methods defined in ApplicationLoadBalancerService
 *
 */

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations="classpath:/appLoadBalancer.xml")
public class ApplicationLoadBalancerTest extends TestCase{
    //The interface to test
    @Inject ApplicationLoadBalancerManagerImpl _appLbSvc;
    
    //The interfaces below are mocked
    @Inject ApplicationLoadBalancerRuleDao _lbDao;
    @Inject LoadBalancingRulesManager _lbMgr;
    @Inject NetworkModel _ntwkModel;
    @Inject AccountManager _accountMgr;
    @Inject FirewallRulesDao _firewallDao;
    @Inject UsageEventDao _usageEventDao;

    
    public static long existingLbId = 1L;
    public static long nonExistingLbId = 2L;
    
    public static long validGuestNetworkId = 1L;
    public static long invalidGuestNetworkId = 2L;
    public static long validPublicNetworkId = 3L;
    
    public static long validAccountId = 1L;
    public static long invalidAccountId = 2L;
    
    public String validRequestedIp = "10.1.1.1";


    
    @Before
    public void setUp() {
        ComponentContext.initComponentsLifeCycle();
        //mockito for .getApplicationLoadBalancer tests
        Mockito.when(_lbDao.findById(1L)).thenReturn(new ApplicationLoadBalancerRuleVO());
        Mockito.when(_lbDao.findById(2L)).thenReturn(null);
        
        //mockito for .deleteApplicationLoadBalancer tests
        Mockito.when(_lbMgr.deleteLoadBalancerRule(existingLbId, true)).thenReturn(true);
        Mockito.when(_lbMgr.deleteLoadBalancerRule(nonExistingLbId, true)).thenReturn(false);
        
        //mockito for .createApplicationLoadBalancer tests
        NetworkVO guestNetwork = new NetworkVO(TrafficType.Guest, null, null, 1,
                null, 1, 1L);
        setId(guestNetwork, validGuestNetworkId);
        guestNetwork.setCidr("10.1.1.1/24");
        
        NetworkVO publicNetwork = new NetworkVO(TrafficType.Public, null, null, 1,
                null, 1, 1L);
   
        Mockito.when(_ntwkModel.getNetwork(validGuestNetworkId)).thenReturn(guestNetwork);
        Mockito.when(_ntwkModel.getNetwork(invalidGuestNetworkId)).thenReturn(null);
        Mockito.when(_ntwkModel.getNetwork(validPublicNetworkId)).thenReturn(publicNetwork);

        Mockito.when(_accountMgr.getAccount(validAccountId)).thenReturn(new AccountVO());
        Mockito.when(_accountMgr.getAccount(invalidAccountId)).thenReturn(null);
        Mockito.when(_ntwkModel.areServicesSupportedInNetwork(validGuestNetworkId, Service.Lb)).thenReturn(true);
        Mockito.when(_ntwkModel.areServicesSupportedInNetwork(invalidGuestNetworkId, Service.Lb)).thenReturn(false);
        
        ApplicationLoadBalancerRuleVO lbRule = new ApplicationLoadBalancerRuleVO("new", "new", 22, 22, "roundrobin",
                validGuestNetworkId, validAccountId, 1L, new Ip(validRequestedIp), validGuestNetworkId, Scheme.Internal);
        Mockito.when(_lbDao.persist(Mockito.any(ApplicationLoadBalancerRuleVO.class))).thenReturn(lbRule);
        
        Mockito.when(_lbMgr.validateLbRule(Mockito.any(LoadBalancingRule.class))).thenReturn(true);
        
        Mockito.when(_firewallDao.setStateToAdd(Mockito.any(FirewallRuleVO.class))).thenReturn(true);
        
        Mockito.when(_accountMgr.getSystemUser()).thenReturn(new UserVO(1));
        Mockito.when(_accountMgr.getSystemAccount()).thenReturn(new AccountVO(2));
        UserContext.registerContext(_accountMgr.getSystemUser().getId(), _accountMgr.getSystemAccount(), null, false);
        
        Mockito.when(_ntwkModel.areServicesSupportedInNetwork(Mockito.anyLong(), Mockito.any(Network.Service.class))).thenReturn(true);
        
        Map<Network.Capability, String> caps = new HashMap<Network.Capability, String>();
        caps.put(Capability.SupportedProtocols, NetUtils.TCP_PROTO);
        Mockito.when(_ntwkModel.getNetworkServiceCapabilities(Mockito.anyLong(), Mockito.any(Network.Service.class))).thenReturn(caps);
        
        
        Mockito.when(_lbDao.countBySourceIp(new Ip(validRequestedIp), validGuestNetworkId)).thenReturn(1L);
        
    }
    
    /**
     * TESTS FOR .getApplicationLoadBalancer
     */
    
    @Test
    //Positive test - retrieve existing lb
    public void searchForExistingLoadBalancer() {
        ApplicationLoadBalancerRule rule = _appLbSvc.getApplicationLoadBalancer(existingLbId);
        assertNotNull("Couldn't find existing application load balancer", rule);
    }
    
    @Test
    //Negative test - try to retrieve non-existing lb
    public void searchForNonExistingLoadBalancer() {
        boolean notFound = false;
        ApplicationLoadBalancerRule rule = null;
        try {
            rule = _appLbSvc.getApplicationLoadBalancer(nonExistingLbId);
            if (rule != null) {
                notFound = false; 
            }
        } catch (InvalidParameterValueException ex) {
            notFound = true;
        }
        
        assertTrue("Found non-existing load balancer; no invalid parameter value exception was thrown", notFound);
    }
    
    /**
     * TESTS FOR .deleteApplicationLoadBalancer
     */
    
    
    @Test
    //Positive test - delete existing lb
    public void deleteExistingLoadBalancer() {
        boolean result = false; 
        
        try {
            result = _appLbSvc.deleteApplicationLoadBalancer(existingLbId);
        } finally {
            assertTrue("Couldn't delete existing application load balancer", result);   
        }
    }
    
    
    @Test
    //Negative test - try to delete non-existing lb
    public void deleteNonExistingLoadBalancer() {
        boolean result = true;
        try {
            result = _appLbSvc.deleteApplicationLoadBalancer(nonExistingLbId);
        } finally {
            assertFalse("Didn't fail when try to delete non-existing load balancer", result);
        }
    }
    
    /**
     * TESTS FOR .createApplicationLoadBalancer
     */
    
    @Test
    //Positive test
    public void createValidLoadBalancer() {
        String expectedExcText = null;
        try {
            _appLbSvc.createApplicationLoadBalancer("alena", "alena", Scheme.Internal, validGuestNetworkId, validRequestedIp,
                            22, 22, "roundrobin", validGuestNetworkId, validAccountId);
        } catch (InsufficientAddressCapacityException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (InsufficientVirtualNetworkCapcityException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (NetworkRuleConflictException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (CloudRuntimeException e) {
            expectedExcText = e.getMessage();
        } finally {
            //expect the exception happen because persistlLbRule() method coudln't be mocked properly due to unability to mock static vars in UsageEventUtils class
            assertEquals("Test failed. The rule wasn't created" + expectedExcText, expectedExcText, "Unable to add lb rule for ip address null");
        }
    }
    
    
    @Test(expected = UnsupportedServiceException.class)
    //Negative test - only internal scheme value is supported in the current release
    public void createPublicLoadBalancer() {
        String expectedExcText = null;
        try {
            _appLbSvc.createApplicationLoadBalancer("alena", "alena", Scheme.Public, validGuestNetworkId, validRequestedIp,
                            22, 22, "roundrobin", validGuestNetworkId, validAccountId);
        } catch (InsufficientAddressCapacityException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (InsufficientVirtualNetworkCapcityException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (NetworkRuleConflictException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (UnsupportedServiceException e) {
            expectedExcText = e.getMessage();
            throw e;
        } finally {
            assertEquals("Test failed. The Public lb rule was created, which shouldn't be supported"
        + expectedExcText, expectedExcText, "Only scheme of type " + Scheme.Internal + " is supported");
        }
    }
    
    
    @Test(expected = InvalidParameterValueException.class)
    //Negative test - invalid SourcePort
    public void createWithInvalidSourcePort() {
        String expectedExcText = null;
        try {
            _appLbSvc.createApplicationLoadBalancer("alena", "alena", Scheme.Internal, validGuestNetworkId, validRequestedIp,
                    65536, 22, "roundrobin", validGuestNetworkId, validAccountId);
        } catch (InsufficientAddressCapacityException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (InsufficientVirtualNetworkCapcityException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (NetworkRuleConflictException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (InvalidParameterValueException e) {
            expectedExcText = e.getMessage();
            throw e;
        } finally {
            assertEquals("Test failed. The rule with invalid source port was created"
        + expectedExcText, expectedExcText, "Invalid value for source port: 65536");
        }
    }
    
    @Test(expected = InvalidParameterValueException.class)
    //Negative test - invalid instancePort
    public void createWithInvalidInstandePort() {
        String expectedExcText = null;
        try {
            _appLbSvc.createApplicationLoadBalancer("alena", "alena", Scheme.Internal, validGuestNetworkId, validRequestedIp,
                    22, 65536, "roundrobin", validGuestNetworkId, validAccountId);
        } catch (InsufficientAddressCapacityException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (InsufficientVirtualNetworkCapcityException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (NetworkRuleConflictException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (InvalidParameterValueException e) {
            expectedExcText = e.getMessage();
            throw e;
        } finally {
            assertEquals("Test failed. The rule with invalid instance port was created"
        + expectedExcText, expectedExcText, "Invalid value for instance port: 65536");
        }
    }
    
    
    @Test(expected = InvalidParameterValueException.class)
    //Negative test - invalid algorithm
    public void createWithInvalidAlgorithm() {
        String expectedExcText = null;
        try {
            _appLbSvc.createApplicationLoadBalancer("alena", "alena", Scheme.Internal, validGuestNetworkId, validRequestedIp,
                    22, 22, "invalidalgorithm", validGuestNetworkId, validAccountId);
        } catch (InsufficientAddressCapacityException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (InsufficientVirtualNetworkCapcityException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (NetworkRuleConflictException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (InvalidParameterValueException e) {
            expectedExcText = e.getMessage();
            throw e;
        } finally {
            assertEquals("Test failed. The rule with invalid algorithm was created"
        + expectedExcText, expectedExcText, "Invalid algorithm: invalidalgorithm");
        }
    }
    
    @Test(expected = InvalidParameterValueException.class)
    //Negative test - invalid sourceNetworkId (of Public type, which is not supported)
    public void createWithInvalidSourceIpNtwk() {
        String expectedExcText = null;
        try {
            _appLbSvc.createApplicationLoadBalancer("alena", "alena", Scheme.Internal, validPublicNetworkId, validRequestedIp,
                    22, 22, "roundrobin", validGuestNetworkId, validAccountId);
        } catch (InsufficientAddressCapacityException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (InsufficientVirtualNetworkCapcityException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (NetworkRuleConflictException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (InvalidParameterValueException e) {
            expectedExcText = e.getMessage();
            throw e;
        } finally {
            assertEquals("Test failed. The rule with invalid source ip network id was created"
        + expectedExcText, expectedExcText, "Only traffic type Guest is supported");
        }
    }
    
    
    @Test(expected = InvalidParameterValueException.class)
    //Negative test - invalid requested IP (outside of guest network cidr range)
    public void createWithInvalidRequestedIp() {
        String expectedExcText = null;
        try {
            _appLbSvc.createApplicationLoadBalancer("alena", "alena", Scheme.Internal, validGuestNetworkId, "10.2.1.1",
                    22, 22, "roundrobin", validGuestNetworkId, validAccountId);
        } catch (InsufficientAddressCapacityException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (InsufficientVirtualNetworkCapcityException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (NetworkRuleConflictException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        
        } catch (InvalidParameterValueException e) {
            expectedExcText = e.getMessage();
            throw e;
        } finally {
            assertEquals("Test failed. The rule with invalid requested ip was created"
        + expectedExcText, expectedExcText, "The requested IP is not in the network's CIDR subnet.");
        }
    }
    
    
    private static NetworkVO setId(NetworkVO vo, long id) {
        NetworkVO voToReturn = vo;
        Class<?> c = voToReturn.getClass();
        try {
            Field f = c.getDeclaredField("id");
            f.setAccessible(true);
            f.setLong(voToReturn, id);
        } catch (NoSuchFieldException ex) {
           return null;
        } catch (IllegalAccessException ex) {
            return null;
        }
        
        return voToReturn;
    }
}