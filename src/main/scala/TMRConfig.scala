package chipyard

import org.chipsalliance.cde.config.{Config}

// --------------
// TMR Config
// --------------

class TMRConfig extends Config(
  new freechips.rocketchip.rocket.WithNHugeCores(1) ++         // single rocket-core
  new chipyard.config.AbstractConfig)
