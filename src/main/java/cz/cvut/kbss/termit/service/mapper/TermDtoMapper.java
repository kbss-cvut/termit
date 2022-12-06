package cz.cvut.kbss.termit.service.mapper;

import cz.cvut.kbss.termit.dto.export.CsvExportTermDto;
import cz.cvut.kbss.termit.dto.export.ExcelExportTermDto;
import cz.cvut.kbss.termit.model.Term;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TermDtoMapper {

    @Mapping(target = "primaryLabel", ignore = true)
    CsvExportTermDto termToCsvExportDto(Term term);

    @Mapping(target = "primaryLabel", ignore = true)
    ExcelExportTermDto termToExcelExportDto(Term term);
}
