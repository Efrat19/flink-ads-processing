# -*- coding: utf-8 -*-
import json
from kafka import KafkaProducer
from scrapy.utils.serialize import ScrapyJSONEncoder

class KafkaPipeline(object):
    @classmethod
    def from_settings(cls, settings):
        """
        :param settings: the current Scrapy settings
        :type settings: scrapy.settings.Settings

        :rtype: A :class:`~KafkaPipeline` instance
        """
        bootstrap_servers = settings.get('SCRAPY_KAFKA_HOSTS')
        topic = settings.get('SCRAPY_KAFKA_ITEM_PIPELINE_TOPIC')
        kafka_producer_client = KafkaProducer(
            bootstrap_servers=bootstrap_servers,
            value_serializer=lambda v: json.dumps(v).encode('utf-8'))
        return cls(kafka_producer_client, topic)

    def __init__(self, producer, topic):
        """
        :type producer: kafka.Producer
        :type topic: str or unicode
        """
        self.producer = producer
        self.topic = topic

    def process_item(self, item, spider):
    #     return deferToThread(self._process_item, item, spider)

    # def _process_item(self, item, spider):
        """
        Overriden method to process the item

        :param item: Item being passed
        :type item: scrapy.item.Item

        :param spider: The current spider being used
        :type spider: scrapy.spider.Spider
        """
        # put spider name in item
        item = dict(item)
        item['spider'] = spider.name
        self.producer.send(self.topic, item)

    def close_spider(self, spider):
        self.producer.close()

    def open_spider(self, spider):
        if not self.producer.bootstrap_connected():
            raise Exception('Scrapy failed to connect to kafka bootstrap')

