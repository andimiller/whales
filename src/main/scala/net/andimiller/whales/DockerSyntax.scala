package net.andimiller.whales

import net.andimiller.whales._

trait DockerSyntax {
  implicit class ProtocolAbleInt(i: Int) {
    def tcp = TCP(i)
    def udp = UDP(i)
  }
}
