import json
from itemadapter import ItemAdapter
from twisted.internet.threads import deferToThread

class JsonWriterPipeline:
    @classmethod
    def from_settings(cls, settings):
        output_path = settings.get('SCRAPY_JSON_OUTPUT_PATH')
        return cls(output_path)

    def __init__(self, output_path):
        self.output_path = output_path
    
    def open_spider(self, spider):
        self.file = open(self.output_path, "w")

    def close_spider(self, spider):
        self.file.close()

    def process_item(self, item, spider):
        return deferToThread(self._process_item, item, spider)

    def _process_item(self, item, spider):
        line = json.dumps(ItemAdapter(item).asdict()) + "\n"
        self.file.write(line)
        self.file.flush()
        return item