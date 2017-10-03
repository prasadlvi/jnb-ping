package com.zaxxer.ping.impl

import jnr.enxio.channels.NativeSocketChannel
import jnr.ffi.Platform
import jnr.ffi.Struct
import jnr.ffi.annotations.Direct
import jnr.ffi.provider.ParameterFlags
import java.net.Inet4Address
import java.net.InetAddress
import java.nio.ByteBuffer

/**
 * Created by brettw on 2017/10/03.
 */
@Suppress("unused")
class NativeIcmpSocketChannel(private val inetAddress : InetAddress, fd : Int) : NativeSocketChannel(fd) {

   companion object {
      @JvmStatic
      fun create(addr : InetAddress) : NativeIcmpSocketChannel {
         val family = if (addr is Inet4Address) LibC.PF_INET else LibC.PF_INET6
         val proto = if (addr is Inet4Address) 1 /*IPPROTO_ICMP*/ else 58 /*IPPROTO_ICMPV6*/
         val fd = libc.socket(family, LibC.SOCK_DGRAM, proto)

         return NativeIcmpSocketChannel(addr, fd)
      }

      @JvmStatic
      fun create(ipAddress : String) : NativeIcmpSocketChannel {
         val addr = InetAddress.getByName(ipAddress)
         val family = if (addr is Inet4Address) LibC.PF_INET else LibC.PF_INET6
         val proto = if (addr is Inet4Address) 1 /*IPPROTO_ICMP*/ else 58 /*IPPROTO_ICMPV6*/
         val fd = libc.socket(family, LibC.SOCK_DGRAM, proto)

         return NativeIcmpSocketChannel(addr, fd)
      }
   }

   override fun write(src : ByteBuffer) : Int {
      begin()
      try {
         val sockAddr : SockAddr
         if (inetAddress is Inet4Address) {
            if (Platform.getNativePlatform().isBSD) {
               sockAddr = BSDSockAddr4()
               val memory = Struct.getMemory(sockAddr, ParameterFlags.DIRECT)
               sockAddr.useMemory(memory)

               sockAddr.sin_family.set(LibC.PF_INET.toShort())
               val rc = libc.inet_pton(LibC.PF_INET, inetAddress.hostAddress, memory.slice(sockAddr.sin_addr.offset()))
               if (rc != 1) {
                  println("Error return code from inet_pton(), should be 1 but is $rc")
               }
            }
            else {
               sockAddr = SockAddr4()
               sockAddr.sin_family.set(htons(LibC.PF_INET.toShort()))
               libc.inet_pton(LibC.PF_INET, inetAddress.hostAddress, Struct.getMemory(sockAddr).getPointer(sockAddr.sin_addr.offset()))
            }
         }
         else {  // IPv6
            error("Not implemented")
         }

         return libc.sendto(fd, src, src.limit(), 0, sockAddr, Struct.size(sockAddr))
      }
      finally {
         end(true)
      }
   }

//   override fun read(dst : ByteBuffer?) : Int {
//      return super.read(dst)
//   }
}