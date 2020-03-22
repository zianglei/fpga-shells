// See LICENSE for license details.
// Copyright (C) 2018-2019 Hex-Five
package sifive.fpgashells.ip.xilinx.ethernetlite

import Chisel._
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.util.{ElaborationArtefacts}

trait PhyPort extends Bundle {
    val phy_tx_clk  = Clock(INPUT)
    val phy_rx_clk  = Clock(INPUT)
    val phy_rx_data = Bits(INPUT, 4)
    val phy_tx_data = Bits(OUTPUT, 4)
    val phy_dv      = Bool(INPUT)
    val phy_rx_er   = Bool(INPUT)
    val phy_tx_en   = Bool(OUTPUT)
    val phy_crs     = Bool(INPUT)
    val phy_col     = Bool(INPUT)
    val phy_rst_n   = Bool(OUTPUT)
}

trait MdioPort extends Bundle {
    val phy_mdc    = Bool(OUTPUT)
    val phy_mdio_i = Bool(INPUT)
    val phy_mdio_o = Bool(OUTPUT)
    val phy_mdio_t = Bool(OUTPUT)
}

//scalastyle:off
//turn off linter: blackbox name must match verilog module
class axiethernetlite() extends BlackBox {

  val io = new Bundle {
    val s_axi_aclk    = Clock(INPUT)
    val s_axi_aresetn = Bool(INPUT)

    //interrupt
    val ip2intc_irpt  = Bool(OUTPUT)

    //axi
    val s_axi_awid    = Bits(INPUT,1)
    val s_axi_awaddr  = Bits(INPUT,13)
    val s_axi_awlen   = Bits(INPUT,8)
    val s_axi_awsize  = Bits(INPUT,3)
    val s_axi_awburst = Bits(INPUT,2)
    val s_axi_awcache = Bits(INPUT,4)
    val s_axi_awvalid = Bool(INPUT)
    val s_axi_awready = Bool(OUTPUT)
    val s_axi_wdata   = Bits(INPUT,32)
    val s_axi_wstrb   = Bits(INPUT, 4)
    val s_axi_wlast   = Bool(INPUT)
    val s_axi_wvalid  = Bool(INPUT)
    val s_axi_wready  = Bool(OUTPUT)
    val s_axi_bid     = Bits(OUTPUT,1)
    val s_axi_bresp   = Bits(OUTPUT,2)
    val s_axi_bvalid  = Bool(OUTPUT)
    val s_axi_bready  = Bool(INPUT)
    val s_axi_arid    = Bits(INPUT,1)
    val s_axi_araddr  = Bits(INPUT,13)
    val s_axi_arlen   = Bits(INPUT,8)
    val s_axi_arsize  = Bits(INPUT,3)
    val s_axi_arburst = Bits(INPUT,2)
    val s_axi_arcache = Bits(INPUT,4)
    val s_axi_arvalid = Bool(INPUT)
    val s_axi_arready = Bool(OUTPUT)
    val s_axi_rid     = Bits(OUTPUT,1)
    val s_axi_rdata   = Bits(OUTPUT,32)
    val s_axi_rresp   = Bits(OUTPUT,2)
    val s_axi_rlast   = Bool(OUTPUT)
    val s_axi_rvalid  = Bool(OUTPUT)
    val s_axi_rready  = Bool(INPUT)

    //phy
    val phy_tx_clk    = Clock(INPUT)
    val phy_rx_clk    = Clock(INPUT)
    val phy_crs       = Bool(INPUT)
    val phy_dv        = Bool(INPUT)
    val phy_rx_data   = Bits(INPUT,4)
    val phy_col       = Bool(INPUT)
    val phy_rx_er     = Bool(INPUT)
    val phy_rst_n     = Bool(OUTPUT)
    val phy_tx_en     = Bool(OUTPUT)
    val phy_tx_data   = Bits(OUTPUT,4)

    //mdio
    val phy_mdio_i    = Bool(INPUT)
    val phy_mdio_o    = Bool(OUTPUT)
    val phy_mdio_t    = Bool(OUTPUT)
    val phy_mdc       = Bool(OUTPUT)
  }

  val modulename = "axiethernetlite"

  ElaborationArtefacts.add(modulename++".vivado.tcl",
   s"""create_ip -vendor xilinx.com -library ip -name axi_ethernetlite -module_name ${modulename} -dir $$ipdir -force
   	|set_property -dict [list   \\
		| CONFIG.AXI_ACLK_FREQ_MHZ {65}		  	  \\
		| CONFIG.C_S_AXI_ID_WIDTH	{1}								\\
		| CONFIG.C_S_AXI_PROTOCOL {AXI4}				\\
		| CONFIG.C_INCLUDE_MDIO {1}									\\
		| CONFIG.C_INCLUDE_INTERNAL_LOOPBACK {0}		\\
		| CONFIG.C_INCLUDE_GLOBAL_BUFFERS {0}				\\
		| CONFIG.C_DUPLEX {1}												\\
		| CONFIG.C_TX_PING_PONG {1}									\\
		| CONFIG.C_RX_PING_PONG {1}									\\
		|] [get_ips ${modulename} ]
		|""".stripMargin)
}

case class ArtyEthernetParams(baseAddress: BigInt)
case object PeripheryArtyEthernetKey extends Field[Seq[ArtyEthernetParams]](Nil)

class EthernetLite(c: ArtyEthernetParams)(implicit p:Parameters) extends LazyModule
{
  val device = new SimpleDevice("ethernetlite", Seq("xlnx,axi-ethernetlite-3.0", "xlnx,xps-ethernetlite-1.00.a"))

  val slave = AXI4SlaveNode(Seq(AXI4SlavePortParameters(
    slaves = Seq(AXI4SlaveParameters(
      address       = AddressSet.misaligned(c.baseAddress, 0x2000),
      resources     = device.reg,
      executable    = false,
      supportsWrite = TransferSizes(1, 64),
      supportsRead  = TransferSizes(1, 64))),
    beatBytes = 4)))

  val intnode = IntSourceNode(IntSourcePortSimple())

  lazy val module = new LazyRawModuleImp(this) {
    val blackbox = Module(new axiethernetlite)

    val (s, _) = slave.in(0)
    val (i, _) = intnode.out(0)

    val io = IO(new Bundle {
      val clockreset = new Bundle {
        val s_axi_aclk = Clock(INPUT)
        val s_axi_aresetn = Bool(INPUT)
      }

      val port = new Bundle with PhyPort
                            with MdioPort {
        val s_axi_aclk = Clock(INPUT)
        val s_axi_aresetn = Bool(INPUT)
      }
    })

    //to top level
    blackbox.io.phy_tx_clk  := io.port.phy_tx_clk
    blackbox.io.phy_rx_clk  := io.port.phy_rx_clk
    blackbox.io.phy_rx_data := io.port.phy_rx_data
    io.port.phy_tx_data     := blackbox.io.phy_tx_data
    blackbox.io.phy_dv      := io.port.phy_dv
    blackbox.io.phy_rx_er   := io.port.phy_rx_er
    io.port.phy_tx_en       := blackbox.io.phy_tx_en
    blackbox.io.phy_crs     := io.port.phy_crs
    blackbox.io.phy_col     := io.port.phy_col
    io.port.phy_rst_n       := blackbox.io.phy_rst_n

    io.port.phy_mdc         := blackbox.io.phy_mdc
    blackbox.io.phy_mdio_i  := io.port.phy_mdio_i
    io.port.phy_mdio_o      := blackbox.io.phy_mdio_o
    io.port.phy_mdio_t      := blackbox.io.phy_mdio_t

    i(0)                    := blackbox.io.ip2intc_irpt 

    blackbox.io.s_axi_aclk := io.clockreset.s_axi_aclk
    blackbox.io.s_axi_aresetn := io.clockreset.s_axi_aresetn

    //s
    //AXI4 signals ordered as per AXI4 Specification (Release D) Section A.2
    //-{lock, cache, prot, qos}
    //-{aclk, aresetn, awuser, wid, wuser, buser, ruser}
    //global signals
    //blackbox.io.s_axi_aclk          := s.aw.aclk
    //blackbox.io.s_axi_aresetn       := s.aw.aresetn
    //slave interface write address
    blackbox.io.s_axi_awid          := s.aw.bits.id
    blackbox.io.s_axi_awaddr        := s.aw.bits.addr
    blackbox.io.s_axi_awlen         := s.aw.bits.len
    blackbox.io.s_axi_awsize        := s.aw.bits.size
    blackbox.io.s_axi_awburst       := s.aw.bits.burst
    //blackbox.io.s_axi_awlock      := s.aw.bits.lock
    blackbox.io.s_axi_awcache       := s.aw.bits.cache
    //blackbox.io.s_axi_awprot      := s.aw.bits.prot
    //blackbox.io.s_axi_awqos       := s.aw.bits.qos
    //blackbox.io.s_axi_awregion    := UInt(0)
    //blackbox.io.awuser            := s.aw.bits.user
    blackbox.io.s_axi_awvalid       := s.aw.valid
    s.aw.ready                      := blackbox.io.s_axi_awready
    //slave interface write data ports
    //blackbox.io.s_axi_wid         := s.w.bits.id
    blackbox.io.s_axi_wdata         := s.w.bits.data
    blackbox.io.s_axi_wstrb         := s.w.bits.strb
    blackbox.io.s_axi_wlast         := s.w.bits.last
    //blackbox.io.s_axi_wuser       := s.w.bits.user
    blackbox.io.s_axi_wvalid        := s.w.valid
    s.w.ready                       := blackbox.io.s_axi_wready
    //slave interface write response
    s.b.bits.id                     := blackbox.io.s_axi_bid
    s.b.bits.resp                   := blackbox.io.s_axi_bresp
    //s.b.bits.user                 := blackbox.io.s_axi_buser
    s.b.valid                       := blackbox.io.s_axi_bvalid
    blackbox.io.s_axi_bready        := s.b.ready
    //slave AXI interface read address ports
    blackbox.io.s_axi_arid          := s.ar.bits.id
    blackbox.io.s_axi_araddr        := s.ar.bits.addr
    blackbox.io.s_axi_arlen         := s.ar.bits.len
    blackbox.io.s_axi_arsize        := s.ar.bits.size
    blackbox.io.s_axi_arburst       := s.ar.bits.burst
    //blackbox.io.s_axi_arlock      := s.ar.bits.lock
    blackbox.io.s_axi_arcache       := s.ar.bits.cache
    //blackbox.io.s_axi_arprot      := s.ar.bits.prot
    //blackbox.io.s_axi_arqos       := s.ar.bits.qos
    //blackbox.io.s_axi_arregion    := UInt(0)
    //blackbox.io.s_axi_aruser      := s.ar.bits.user
    blackbox.io.s_axi_arvalid       := s.ar.valid
    s.ar.ready                      := blackbox.io.s_axi_arready
    //slave AXI interface read data ports
    s.r.bits.id                     := blackbox.io.s_axi_rid
    s.r.bits.data                   := blackbox.io.s_axi_rdata
    s.r.bits.resp                   := blackbox.io.s_axi_rresp
    s.r.bits.last                   := blackbox.io.s_axi_rlast
    //s.r.bits.ruser                := blackbox.io.s_axi_ruser
    s.r.valid                       := blackbox.io.s_axi_rvalid
    blackbox.io.s_axi_rready        := s.r.ready
  }
}
