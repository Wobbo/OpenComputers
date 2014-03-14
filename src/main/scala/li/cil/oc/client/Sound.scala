package li.cil.oc.client

import cpw.mods.fml.client.FMLClientHandler
import java.util.{TimerTask, Timer, UUID}
import li.cil.oc.{OpenComputers, Settings}
import li.cil.oc.common.tileentity
import net.minecraft.client.Minecraft
import net.minecraft.tileentity.TileEntity
import net.minecraftforge.client.event.sound.SoundLoadEvent
import net.minecraftforge.event.world.{WorldEvent, ChunkEvent}
import paulscode.sound.{SoundSystem, SoundSystemConfig}
import scala.collection.convert.WrapAsScala._
import scala.collection.mutable
import java.util.logging.Level
import net.minecraft.client.audio.{SoundPoolEntry, SoundManager, SoundCategory}
import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.relauncher.ReflectionHelper
import net.minecraft.util.ResourceLocation
import java.net.{MalformedURLException, URLConnection, URLStreamHandler, URL}

object Sound {
  private val sources = mutable.Map.empty[TileEntity, PseudoLoopingStream]

  private val commandQueue = mutable.PriorityQueue.empty[Command]

  private var lastVolume = FMLClientHandler.instance.getClient.gameSettings.getSoundLevel(SoundCategory.BLOCKS)

  private val updateTimer = new Timer("OpenComputers-SoundUpdater", true)
  if (Settings.get.soundVolume > 0) {
    updateTimer.scheduleAtFixedRate(new TimerTask {
      override def run() {
        updateVolume()
        processQueue()
      }
    }, 500, 50)
  }

  // Set in init event.
  var manager: SoundManager = _

  def soundSystem: SoundSystem = ReflectionHelper.getPrivateValue(classOf[SoundManager], manager, "sndSystem")

  private def updateVolume() {
    val volume = FMLClientHandler.instance.getClient.gameSettings.getSoundLevel(SoundCategory.BLOCKS)
    if (volume != lastVolume) {
      lastVolume = volume
      sources.synchronized {
        for (sound <- sources.values) {
          sound.updateVolume()
        }
      }
    }
  }

  private def processQueue() {
    if (!commandQueue.isEmpty) {
      commandQueue.synchronized {
        while (!commandQueue.isEmpty && commandQueue.head.when < System.currentTimeMillis()) {
          try commandQueue.dequeue()() catch {
            case t: Throwable => OpenComputers.log.log(Level.WARNING, "Error processing sound command.", t)
          }
        }
      }
    }
  }

  def startLoop(tileEntity: TileEntity, name: String, volume: Float = 1f, delay: Long = 0) {
    if (Settings.get.soundVolume > 0) {
      commandQueue.synchronized {
        commandQueue += new StartCommand(System.currentTimeMillis() + delay, tileEntity, name, volume)
      }
    }
  }

  def stopLoop(tileEntity: TileEntity) {
    if (Settings.get.soundVolume > 0) {
      commandQueue.synchronized {
        commandQueue += new StopCommand(tileEntity)
      }
    }
  }

  def updatePosition(tileEntity: TileEntity) {
    if (Settings.get.soundVolume > 0) {
      commandQueue.synchronized {
        commandQueue += new UpdatePositionCommand(tileEntity)
      }
    }
  }

  @SubscribeEvent
  def onSoundLoad(event: SoundLoadEvent) {
    manager = event.manager
  }

  @SubscribeEvent
  def onChunkUnload(event: ChunkEvent.Unload) {
    cleanup(event.getChunk.chunkTileEntityMap.values)
  }

  @SubscribeEvent
  def onWorldUnload(event: WorldEvent.Unload) {
    cleanup(event.world.loadedTileEntityList)
  }

  private def cleanup[_](list: Iterable[_]) {
    commandQueue.synchronized(commandQueue.clear())
    sources.synchronized {
      list.foreach {
        case robot: tileentity.RobotProxy => stahp(robot.robot)
        case tileEntity: TileEntity => stahp(tileEntity)
        case _ =>
      }
    }
  }

  private def stahp(tileEntity: TileEntity) = sources.remove(tileEntity) match {
    case Some(sound) => sound.stop()
    case _ =>
  }

  private abstract class Command(val when: Long, val tileEntity: TileEntity) extends Ordered[Command] {
    def apply()

    override def compare(that: Command) = (that.when - when).toInt
  }

  private class StartCommand(when: Long, tileEntity: TileEntity, val name: String, val volume: Float) extends Command(when, tileEntity) {
    override def apply() {
      sources.synchronized {
        sources.getOrElseUpdate(tileEntity, new PseudoLoopingStream(tileEntity, volume)).play(name)
      }
    }
  }

  private class StopCommand(tileEntity: TileEntity) extends Command(0, tileEntity) {
    override def apply() {
      sources.synchronized {
        sources.remove(tileEntity) match {
          case Some(sound) => sound.stop()
          case _ =>
        }
      }
      commandQueue.synchronized {
        // Remove all other commands for this tile entity from the queue. This
        // is inefficient, but we generally don't expect the command queue to
        // be very long, so this should be OK.
        commandQueue ++= commandQueue.dequeueAll.filter(_.tileEntity != tileEntity)
      }
    }
  }

  private class UpdatePositionCommand(tileEntity: TileEntity) extends Command(0, tileEntity) {
    override def apply() {
      sources.synchronized {
        sources.get(tileEntity) match {
          case Some(sound) => sound.updatePosition()
          case _ =>
        }
      }
    }
  }

  private class PseudoLoopingStream(val tileEntity: TileEntity, val volume: Float, val source: String = UUID.randomUUID.toString) {
    var initialized = false

    def updateVolume() {
      soundSystem.setVolume(source, lastVolume * volume * Settings.get.soundVolume)
    }

    def updatePosition() {
      soundSystem.setPosition(source, tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord)
    }

    def play(name: String) {
      val resourceName = s"${Settings.resourceDomain}:$name"
      val sound = Minecraft.getMinecraft.getSoundHandler.getSound(new ResourceLocation(resourceName))
      val resource = (sound.func_148720_g: SoundPoolEntry).getSoundPoolEntryLocation
      if (!initialized) {
        initialized = true
        soundSystem.newSource(false, source, toUrl(resource), resource.toString, true, tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord, SoundSystemConfig.ATTENUATION_LINEAR, 16.0f)
        updateVolume()
        soundSystem.activate(source)
      }
      soundSystem.play(source)
    }

    def stop() {
      soundSystem.stop(source)
      soundSystem.removeSource(source)
    }
  }

  // This is copied from SoundManager.getURLForSoundResource, which is private.
  private def toUrl(resource: ResourceLocation): URL = {
    val name = s"mcsounddomain:${resource.getResourceDomain}:${resource.getResourcePath}"
    try {
      new URL(null, name, new URLStreamHandler {
        protected def openConnection(url: URL): URLConnection = new URLConnection(url) {
          def connect() {
          }

          override def getInputStream = Minecraft.getMinecraft.getResourceManager.getResource(resource).getInputStream
        }
      })
    }
    catch {
      case _: MalformedURLException => null
    }
  }
}
