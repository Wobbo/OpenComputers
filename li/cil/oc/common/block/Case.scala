package li.cil.oc.common.block

import li.cil.oc.Config
import li.cil.oc.common.tileentity
import net.minecraft.client.renderer.texture.IconRegister
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.util.Icon
import net.minecraft.world.IBlockAccess
import net.minecraft.world.World
import net.minecraftforge.common.ForgeDirection

class Case(val parent: SimpleDelegator) extends Computer with SimpleDelegate {
  val unlocalizedName = "Case"

  // ----------------------------------------------------------------------- //

  private object Icons {
    val on = Array.fill[Icon](6)(null)
    val off = Array.fill[Icon](6)(null)
  }

  override def getBlockTextureFromSide(world: IBlockAccess, x: Int, y: Int, z: Int, worldSide: ForgeDirection, localSide: ForgeDirection) = {
    getIcon(localSide, world.getBlockTileEntity(x, y, z) match {
      case computer: tileentity.Case => computer.isOn
      case _ => false
    })
  }

  override def icon(side: ForgeDirection) = getIcon(side, isOn = false)

  private def getIcon(side: ForgeDirection, isOn: Boolean) =
    Some(if (isOn) Icons.on(side.ordinal) else Icons.off(side.ordinal))

  override def registerIcons(iconRegister: IconRegister) = {
    Icons.off(ForgeDirection.DOWN.ordinal) = iconRegister.registerIcon(Config.resourceDomain + ":case_top")
    Icons.on(ForgeDirection.DOWN.ordinal) = Icons.off(ForgeDirection.DOWN.ordinal)
    Icons.off(ForgeDirection.UP.ordinal) = Icons.off(ForgeDirection.DOWN.ordinal)
    Icons.on(ForgeDirection.UP.ordinal) = Icons.off(ForgeDirection.UP.ordinal)

    Icons.off(ForgeDirection.NORTH.ordinal) = iconRegister.registerIcon(Config.resourceDomain + ":case_back")
    Icons.on(ForgeDirection.NORTH.ordinal) = iconRegister.registerIcon(Config.resourceDomain + ":case_back_on")

    Icons.off(ForgeDirection.SOUTH.ordinal) = iconRegister.registerIcon(Config.resourceDomain + ":case_front")
    Icons.on(ForgeDirection.SOUTH.ordinal) = Icons.off(ForgeDirection.SOUTH.ordinal)

    Icons.off(ForgeDirection.WEST.ordinal) = iconRegister.registerIcon(Config.resourceDomain + ":case_side")
    Icons.on(ForgeDirection.WEST.ordinal) = iconRegister.registerIcon(Config.resourceDomain + ":case_side_on")
    Icons.off(ForgeDirection.EAST.ordinal) = Icons.off(ForgeDirection.WEST.ordinal)
    Icons.on(ForgeDirection.EAST.ordinal) = Icons.on(ForgeDirection.WEST.ordinal)
  }

  // ----------------------------------------------------------------------- //

  override def createTileEntity(world: World) = Some(new tileentity.Case(world.isRemote))

  // ----------------------------------------------------------------------- //

  // TODO do we have to manually sync the client since we can only check this on the server side?
  override def onBlockRemovedBy(world: World, x: Int, y: Int, z: Int, player: EntityPlayer) =
    world.getBlockTileEntity(x, y, z) match {
      case computer: tileentity.Case if !world.isRemote =>
        computer.isUser(player.getCommandSenderName)
      case _ => super.onBlockRemovedBy(world, x, y, z, player)
    }
}