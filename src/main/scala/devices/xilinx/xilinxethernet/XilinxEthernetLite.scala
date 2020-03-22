// See LICENSE for license details.
// Copyright (C) 2018-2019 Hex-Five
package sifive.fpgashells.devices.xilinx.xilinxethernetlite

import Chisel._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.subsystem.{CrossesToOnlyOneClockDomain, CacheBlockBytes}
import sifive.fpgashells.ip.xilinx.ethernetlite.{EthernetLite, PhyPort, MdioPort, ArtyEthernetParams}

class XilinxEthernetLite(c: ArtyEthernetParams)(implicit p: Parameters, val crossing: ClockCrossingType = NoCrossing)
  extends LazyModule with CrossesToOnlyOneClockDomain
{
  val ethernetlite = LazyModule(new EthernetLite(c))

  val slave: TLInwardNode =
    (ethernetlite.slave
      := AXI4Buffer()
      := AXI4UserYanker()
      := AXI4Deinterleaver(p(CacheBlockBytes))
      := AXI4IdIndexer(idBits=4)
      := TLToAXI4(adapterName = Some("ethernetlite")))

  val intnode: IntOutwardNode = ethernetlite.intnode

  lazy val module = new LazyModuleImp(this) {
    val io = IO(new Bundle {
      val port = new Bundle with PhyPort
                            with MdioPort {
          val s_axi_aclk = Clock(INPUT)
      }
    })

    ethernetlite.module.io.clockreset.s_axi_aclk := io.port.s_axi_aclk
    ethernetlite.module.io.clockreset.s_axi_aresetn := ~reset

    io.port <> ethernetlite.module.io.port
  }
}
