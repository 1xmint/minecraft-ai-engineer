package dev.minecraftai.engineer;

/** Contract selection is data validation, never a claim of functional verification. */
final class EngineeringContracts {
    private EngineeringContracts() { }
    static MechanismCatalog.Contract contract(Blueprint blueprint) {
        if(ToggleMemoryIntention.describes(blueprint.source())){
            ToggleMemoryIntention.validated(blueprint);var ports=new java.util.ArrayList<MechanismCatalog.Port>();
            for(var value:blueprint.source().getAsJsonArray("ports")){var port=value.getAsJsonObject();ports.add(new MechanismCatalog.Port(port.get("name").getAsString(),Blueprint.pos(port.getAsJsonObject("pos")).rotate(Blueprint.rotation(blueprint.rotation())).offset(blueprint.anchor()),port.get("kind").getAsString().equals("input")));}
            // Describes intended external ports only. Generic test/recovery explicitly refuse this type.
            return new MechanismCatalog.Contract("toggle-memory",ports,java.util.List.of(),8);
        }

        if(ImportedBlueprint.describes(blueprint.source())){
            ImportedBlueprint.validate(blueprint);
            return new MechanismCatalog.Contract("schematic-geometry",java.util.List.of(),java.util.List.of(),0);
        }
        if(ConnectedIntention.describes(blueprint.source())){
            if(ConnectedIntention.fullAdder(blueprint.source())){
                var source=ConnectedIntention.validated(blueprint).source();var ports=new java.util.ArrayList<MechanismCatalog.Port>();
                for(var value:source.getAsJsonArray("ports")){var port=value.getAsJsonObject();ports.add(new MechanismCatalog.Port(port.get("name").getAsString(),Blueprint.pos(port.getAsJsonObject("pos")).rotate(Blueprint.rotation(blueprint.rotation())).offset(blueprint.anchor()),port.get("kind").getAsString().equals("input")));}
                return new MechanismCatalog.Contract("full-adder",ports,java.util.List.of(),8);
            }
            ConnectedIntention.validated(blueprint);var design=new HalfAdderDesign();
            var ports=new java.util.ArrayList<MechanismCatalog.Port>();
            for(var pin:design.pins())if(pin.role()==HalfAdderDesign.Role.EXTERNAL_CONTROL || pin.role()==HalfAdderDesign.Role.RESULT)
                ports.add(new MechanismCatalog.Port(pin.id(),pin.pos().rotate(Blueprint.rotation(blueprint.rotation())).offset(blueprint.anchor()),pin.role()==HalfAdderDesign.Role.EXTERNAL_CONTROL));
            return new MechanismCatalog.Contract("half-adder",ports,java.util.List.of(),8);
        }
        if(AnalogIntention.describes(blueprint.source())){
            var design=AnalogIntention.validated(blueprint).design();var ports=new java.util.ArrayList<MechanismCatalog.Port>();
            for(var selector:design.selectors())ports.add(new MechanismCatalog.Port("bank-"+selector.bank()+"-"+selector.index(),selector.pos(),true));
            ports.add(new MechanismCatalog.Port("output",design.output(),false));
            return new MechanismCatalog.Contract("comparator-"+design.mode(),ports,java.util.List.of(),8,null,design.mode());
        }
        return GeneratedBlueprint.isGenerated(blueprint)?GeneratedBlueprint.contract(blueprint):MechanismCatalog.contract(blueprint);
    }
}
