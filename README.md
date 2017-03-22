[![Logo](http://plvision.eu/wp-content/themes/plvision/img/plvision-logo.png)](http://plvision.eu/)

## Switchover forwarding for ONOS

**Link quality monitor application**

The ‘**Link quality monitor**’ application intended for monitoring quality of all links. Application periodically sends L2 probe packets from each device to the all neighbors and receives them on the neighbor device. Result of quality calculated as percent of number received packets to number of packets which was sent. Each network link has two direction, so quality of link calculated for both directions separately.

The application has own UI overlay in the ONOS topology page. This overlay allows visual viewing of monitoring results on the current network topology. The overlay has two modes:

- Link mode  - (shortcut key ‘**F**’) shows all problem links with their quality values;
- Device mode -  (shortcut key ‘**V**’) shows all problem egress links with their quality values for selected device.


The ‘Link quality monitor’ implemented as service, so other applications can use link quality monitoring results in the own purposes.

**Switchover Forwarding application**

The ‘**Switchover Forwarding**’ application intended for traffic forwarding according to quality of links.

This application used the information about quality of links from application 'Link quality monitor' and selects paths for traffic with best quality of links. The application dynamically selects the backup path for traffic when quality of links of primary path less of threshold value. If quality of links in the primary path is restored, the application automatically returns traffic to primary path. If backup paths also contain links with bad quality, the application selects the path with minimum loss of data.

The detailed information about compilation and installation of applications into ONOS can be found on the [ONOS Wiki](https://wiki.onosproject.org/display/ONOS/Wiki+Home) website.