package li.cil.oc.integration.opencomputers

import li.cil.oc.api
import li.cil.oc.api.driver.EnvironmentAware
import li.cil.oc.api.driver.EnvironmentHost
import li.cil.oc.common
import li.cil.oc.common.Slot
import li.cil.oc.common.init.Items
import li.cil.oc.server.component
import net.minecraft.item.ItemStack

object DriverGraphicsCard extends Item with EnvironmentAware {
  override def worksWith(stack: ItemStack) =
    isOneOf(stack, api.Items.get("graphicsCard1"), api.Items.get("graphicsCard2"), api.Items.get("graphicsCard3"))

  override def createEnvironment(stack: ItemStack, host: EnvironmentHost) =
    tier(stack) match {
      case 0 => new component.GraphicsCard.Tier1()
      case 1 => new component.GraphicsCard.Tier2()
      case 2 => new component.GraphicsCard.Tier3()
      case _ => null
    }

  override def slot(stack: ItemStack) = Slot.Card

  override def tier(stack: ItemStack) =
    Items.multi.subItem(stack) match {
      case Some(gpu: common.item.GraphicsCard) => gpu.tier
      case _ => 0
    }

  override def providedEnvironment(stack: ItemStack) = classOf[component.GraphicsCard]
}
