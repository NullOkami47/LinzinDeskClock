package com.linzin.linzindeskclock.power

import org.junit.Assert.*
import org.junit.Test

class LowBatteryPolicyTest {
    @Test fun onlyBelowSelectedThresholdTriggers() {
        assertEquals(LowBatteryAction.DIM,LowBatteryPolicy.action(19,false,20,LowBatteryAction.DIM))
        assertEquals(LowBatteryAction.OFF,LowBatteryPolicy.action(20,false,20,LowBatteryAction.DIM))
        assertEquals(LowBatteryAction.EXIT,LowBatteryPolicy.action(9,false,10,LowBatteryAction.EXIT))
    }
    @Test fun chargingAndDisabledModesDoNotExitOrDim() {
        assertEquals(LowBatteryAction.OFF,LowBatteryPolicy.action(1,true,20,LowBatteryAction.EXIT))
        assertEquals(LowBatteryAction.OFF,LowBatteryPolicy.action(1,false,20,LowBatteryAction.OFF))
    }
    @Test fun invalidBatteryReadingsFailWithoutExiting() {
        assertEquals(LowBatteryAction.OFF,LowBatteryPolicy.action(-1,false,20,LowBatteryAction.EXIT))
        assertEquals(LowBatteryAction.OFF,LowBatteryPolicy.action(101,false,20,LowBatteryAction.EXIT))
    }
    @Test fun dimCapsBrightnessWithoutRaisingAlreadyDimManualValue() {
        assertEquals(.08f,LowBatteryPolicy.brightness(.6f,.5f,LowBatteryAction.DIM),.001f)
        assertEquals(.03f,LowBatteryPolicy.brightness(.03f,.5f,LowBatteryAction.DIM),.001f)
        assertEquals(.05f,LowBatteryPolicy.brightness(-1f,.05f,LowBatteryAction.DIM),.001f)
        assertEquals(-1f,LowBatteryPolicy.brightness(-1f,.5f,LowBatteryAction.OFF),.001f)
    }
}
