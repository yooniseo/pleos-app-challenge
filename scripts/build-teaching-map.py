"""Create the offline, attributed teaching-map fixture from an OSM API XML extract.
Usage: python scripts/build-teaching-map.py extract.osm output.json
Road centrelines/building polygons are OSM data. Missing widths use illustrative defaults.
"""
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

root = ET.parse(sys.argv[1]).getroot()
nodes = {n.get('id'): [float(n.get('lat')), float(n.get('lon'))] for n in root.findall('node')}
roads, areas = [], []
road_types = {'primary', 'secondary', 'tertiary', 'primary_link', 'secondary_link',
              'tertiary_link', 'residential', 'service', 'unclassified'}
for way in root.findall('way'):
    tags = {t.get('k'): t.get('v') for t in way.findall('tag')}
    points = [nodes[n.get('ref')] for n in way.findall('nd') if n.get('ref') in nodes]
    if len(points) < 2:
        continue
    highway = tags.get('highway', '')
    if highway in road_types:
        major = highway in {'primary', 'secondary'}
        lanes = int(tags.get('lanes', '3' if major else '1' if highway.endswith('_link') else '2'))
        roads.append(dict(id=way.get('id'), points=points, name=tags.get('name', ''),
                          width=lanes*3.5, oneway=tags.get('oneway') == 'yes', lanes=lanes, major=major))
    elif tags.get('building') or tags.get('landuse') in {'grass', 'forest'} or tags.get('leisure') == 'park':
        areas.append(dict(points=points, green=not bool(tags.get('building'))))
fixture = dict(source='OpenStreetMap contributors; snapshot 2026-11-12; widths illustrative where absent; educational crossing overlays are not surveyed',
               roads=roads, areas=areas)
Path(sys.argv[2]).write_text(json.dumps(fixture, ensure_ascii=False, separators=(',', ':')), encoding='utf-8')
