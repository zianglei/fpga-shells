// See LICENSE for license details.
// Copyright (C) 2018-2019 Hex-Five
package sifive.fpgashells.devices.xilinx.xilinxethernetlite

import Chisel._
import freechips.rocketchip.diplomacy.{LazyModule, LazyModuleImp, BufferParams}
import freechips.rocketchip.subsystem.BaseSubsystem
import freechips.rocketchip.tilelink._
import freechips.rocketchip.interrupts.IntSyncCrossingSink

import sifive.fpgashells.ip.xilinx.ethernetlite.{PeripheryArtyEthernetKey, PhyPort, MdioPort}

trait HasPeripheryXilinxEthernetLite { this: BaseSubsystem =>
  val xilinxethernetlite = LazyModule(new XilinxEthernetLite(p(PeripheryArtyEthernetKey).head))
  private val cname = "xilinxethernetlite"
  sbus.coupleTo(s"slave_named_$cname") { xilinxethernetlite.crossTLIn(xilinxethernetlite.slave) :*= TLWidthWidget(sbus.beatBytes) :*= _ }
  ibus.fromSync := xilinxethernetlite.crossIntOut(xilinxethernetlite.intnode)
}

trait HasPeripheryXilinxEthernetLiteModuleImp extends LazyModuleImp {
  val outer: HasPeripheryXilinxEthernetLite
  val phy = IO(new Bundle with PhyPort with MdioPort )

  phy <> outer.xilinxethernetlite.module.io.port
}
