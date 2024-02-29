# Define here the models for your scraped items
#
# See documentation in:
# https://docs.scrapy.org/en/latest/topics/items.html

import scrapy


class AdItem(scrapy.Item):
    city = scrapy.Field()
    ad_id = scrapy.Field()
    posted_date = scrapy.Field()
    scraped_time = scrapy.Field()
    pass
